package io.github.rateLimiter.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.rateLimiter.annotation.RateLimit;
import io.github.rateLimiter.config.RateLimiterProperties;
import io.github.rateLimiter.exception.RateLimitException;
import io.github.rateLimiter.model.RateLimitContext;
import io.github.rateLimiter.model.RateLimitResult;
import io.github.rateLimiter.service.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Redis-based implementation of RateLimitService
 * 
 * <p>Uses Redis for distributed rate limiting with Lua scripts for atomic operations.
 * Supports all rate limiting algorithms with optional local caching for improved performance.
 * 
 * @author Smart Rate Limiter Team
 * @since 1.0.0
 */
public class RedisRateLimitService implements RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitService.class);

    private final StringRedisTemplate redisTemplate;
    private final RateLimiterProperties properties;
    private final Cache<String, RateLimitResult> localCache;
    private final DefaultRedisScript<Long> slidingWindowScript;
    private final DefaultRedisScript<Long> fixedWindowScript;
    private final DefaultRedisScript<Long> tokenBucketScript;
    private final DefaultRedisScript<Long> leakyBucketScript;

    public RedisRateLimitService(StringRedisTemplate redisTemplate, RateLimiterProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.localCache = buildLocalCache();
        this.slidingWindowScript = createSlidingWindowScript();
        this.fixedWindowScript = createFixedWindowScript();
        this.tokenBucketScript = createTokenBucketScript();
        this.leakyBucketScript = createLeakyBucketScript();
    }

    @Override
    public RateLimitResult checkRateLimit(RateLimitContext context) {
        String cacheKey = context.getKey() + ":" + context.getAlgorithm();
        
        // Check local cache first if enabled
        if (properties.getCache().isEnabled()) {
            RateLimitResult cachedResult = localCache.getIfPresent(cacheKey);
            if (cachedResult != null && cachedResult.isAllowed()) {
                return cachedResult;
            }
        }

        try {
            RateLimitResult result = executeRateLimitCheck(context);
            
            // Cache positive results briefly to reduce Redis load
            if (properties.getCache().isEnabled() && result.isAllowed()) {
                localCache.put(cacheKey, result);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Rate limit check failed for key: {}", context.getKey(), e);
            throw new RateLimitException("Rate limit check failed", e);
        }
    }

    @Override
    public RateLimitResult getRateLimitStatus(RateLimitContext context) {
        try {
            return executeRateLimitStatus(context);
        } catch (Exception e) {
            log.error("Failed to get rate limit status for key: {}", context.getKey(), e);
            throw new RateLimitException("Failed to get rate limit status", e);
        }
    }

    @Override
    public boolean resetRateLimit(String key) {
        try {
            // Remove from local cache
            if (properties.getCache().isEnabled()) {
                localCache.invalidate(key);
            }
            
            // Remove from Redis (simple key deletion)
            return Boolean.TRUE.equals(redisTemplate.delete(key));
            
        } catch (Exception e) {
            log.error("Failed to reset rate limit for key: {}", key, e);
            return false;
        }
    }

    @Override
    public boolean isHealthy() {
        try {
            // Simple ping to check Redis connectivity
            String result = redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<String>) connection -> {
                byte[] pingResult = Objects.requireNonNull(connection.ping()).getBytes();
                return new String(pingResult);
            });
            return "PONG".equals(result);
        } catch (Exception e) {
            log.debug("Redis health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getStorageType() {
        return "redis";
    }

    /**
     * Executes the actual rate limit check based on algorithm
     */
    private RateLimitResult executeRateLimitCheck(RateLimitContext context) {
        long currentTime = System.currentTimeMillis();
        
        switch (context.getAlgorithm()) {
            case SLIDING_WINDOW:
                return executeSlidingWindowCheck(context, currentTime);
            case FIXED_WINDOW:
                return executeFixedWindowCheck(context, currentTime);
            case TOKEN_BUCKET:
                return executeTokenBucketCheck(context, currentTime);
            case LEAKY_BUCKET:
                return executeLeakyBucketCheck(context, currentTime);
            default:
                throw new IllegalArgumentException("Unsupported algorithm: " + context.getAlgorithm());
        }
    }

    /**
     * Gets rate limit status without consuming permits
     */
    private RateLimitResult executeRateLimitStatus(RateLimitContext context) {
        // For status checks, we'll use a non-consuming version of the scripts
        // This is a simplified implementation - in production, you might want separate scripts
        try {
            String key = context.getKey();
            long windowStart = System.currentTimeMillis() - (context.getWindowSeconds() * 1000L);
            
            if (context.getAlgorithm() == RateLimit.LimitAlgorithm.SLIDING_WINDOW) {
                // Count current requests in sliding window without adding new one
                Long currentCount = redisTemplate.opsForZSet().count(key, windowStart, System.currentTimeMillis());
                long remaining = Math.max(0, context.getPermits() - currentCount);
                
                return RateLimitResult.builder()
                        .allowed(currentCount < context.getPermits())
                        .key(key)
                        .remainingPermits(remaining)
                        .totalPermits(context.getPermits())
                        .algorithm(context.getAlgorithm().name())
                        .dimension(context.getDimension().name())
                        .build();
            } else {
                // For other algorithms, we need to check stored state
                String countKey = key + ":count";
                String countStr = redisTemplate.opsForValue().get(countKey);
                long currentCount = countStr != null ? Long.parseLong(countStr) : 0;
                long remaining = Math.max(0, context.getPermits() - currentCount);
                
                return RateLimitResult.builder()
                        .allowed(currentCount < context.getPermits())
                        .key(key)
                        .remainingPermits(remaining)
                        .totalPermits(context.getPermits())
                        .algorithm(context.getAlgorithm().name())
                        .dimension(context.getDimension().name())
                        .build();
            }
        } catch (Exception e) {
            log.warn("Failed to get rate limit status, returning default: {}", e.getMessage());
            return RateLimitResult.builder()
                    .allowed(true)
                    .key(context.getKey())
                    .remainingPermits(context.getPermits())
                    .totalPermits(context.getPermits())
                    .algorithm(context.getAlgorithm().name())
                    .dimension(context.getDimension().name())
                    .build();
        }
    }

    /**
     * Sliding window algorithm implementation
     */
    private RateLimitResult executeSlidingWindowCheck(RateLimitContext context, long currentTime) {
        Long result = redisTemplate.execute(slidingWindowScript, 
                Collections.singletonList(context.getKey()),
                String.valueOf(context.getWindowSeconds()),
                String.valueOf(context.getPermits()),
                String.valueOf(currentTime));

        boolean allowed = result != null && result == 1;
        
        // Get current count for remaining permits calculation
        long windowStart = currentTime - (context.getWindowSeconds() * 1000L);
        Long currentCount = redisTemplate.opsForZSet().count(context.getKey(), windowStart, currentTime);
        long remaining = allowed ? Math.max(0, context.getPermits() - currentCount) : 0;

        return RateLimitResult.builder()
                .allowed(allowed)
                .key(context.getKey())
                .remainingPermits(remaining)
                .totalPermits(context.getPermits())
                .resetTime(Instant.ofEpochMilli(currentTime + (context.getWindowSeconds() * 1000L)))
                .retryAfterSeconds(allowed ? null : (long) context.getWindowSeconds())
                .algorithm(context.getAlgorithm().name())
                .dimension(context.getDimension().name())
                .build();
    }

    /**
     * Fixed window algorithm implementation
     */
    private RateLimitResult executeFixedWindowCheck(RateLimitContext context, long currentTime) {
        Long result = redisTemplate.execute(fixedWindowScript,
                Collections.singletonList(context.getKey()),
                String.valueOf(context.getWindowSeconds()),
                String.valueOf(context.getPermits()),
                String.valueOf(currentTime));

        boolean allowed = result != null && result <= context.getPermits();
        long remaining = allowed ? Math.max(0, context.getPermits() - result) : 0;

        // Calculate next window start
        long windowSize = context.getWindowSeconds() * 1000L;
        long currentWindow = currentTime / windowSize;
        long nextWindowStart = (currentWindow + 1) * windowSize;

        return RateLimitResult.builder()
                .allowed(allowed)
                .key(context.getKey())
                .remainingPermits(remaining)
                .totalPermits(context.getPermits())
                .resetTime(Instant.ofEpochMilli(nextWindowStart))
                .retryAfterSeconds(allowed ? null : (nextWindowStart - currentTime) / 1000)
                .algorithm(context.getAlgorithm().name())
                .dimension(context.getDimension().name())
                .build();
    }

    /**
     * Token bucket algorithm implementation
     */
    private RateLimitResult executeTokenBucketCheck(RateLimitContext context, long currentTime) {
        int bucketCapacity = context.getBucketCapacity() > 0 ? context.getBucketCapacity() : context.getPermits();
        double refillRate = context.getRefillRate() > 0 ? context.getRefillRate() : 
                (double) context.getPermits() / context.getWindowSeconds();

        Long result = redisTemplate.execute(tokenBucketScript,
                Collections.singletonList(context.getKey()),
                String.valueOf(bucketCapacity),
                String.valueOf(refillRate),
                String.valueOf(currentTime));

        boolean allowed = result != null && result >= 0;
        long remaining = allowed ? result : 0;

        return RateLimitResult.builder()
                .allowed(allowed)
                .key(context.getKey())
                .remainingPermits(remaining)
                .totalPermits(bucketCapacity)
                .retryAfterSeconds(allowed ? null : (long) (1.0 / refillRate))
                .algorithm(context.getAlgorithm().name())
                .dimension(context.getDimension().name())
                .build();
    }

    /**
     * Leaky bucket algorithm implementation
     */
    private RateLimitResult executeLeakyBucketCheck(RateLimitContext context, long currentTime) {
        double leakRate = (double) context.getPermits() / context.getWindowSeconds();

        Long result = redisTemplate.execute(leakyBucketScript,
                Collections.singletonList(context.getKey()),
                String.valueOf(context.getPermits()),
                String.valueOf(leakRate),
                String.valueOf(currentTime));

        boolean allowed = result != null && result >= 0;
        long remaining = allowed ? Math.max(0, context.getPermits() - result) : 0;

        return RateLimitResult.builder()
                .allowed(allowed)
                .key(context.getKey())
                .remainingPermits(remaining)
                .totalPermits(context.getPermits())
                .retryAfterSeconds(allowed ? null : (long) (1.0 / leakRate))
                .algorithm(context.getAlgorithm().name())
                .dimension(context.getDimension().name())
                .build();
    }

    /**
     * Builds local cache based on configuration
     */
    private Cache<String, RateLimitResult> buildLocalCache() {
        if (!properties.getCache().isEnabled()) {
            return null;
        }

        return Caffeine.newBuilder()
                .maximumSize(properties.getCache().getMaxSize())
                .expireAfterWrite(properties.getCache().getExpireAfterWrite())
                .expireAfterAccess(properties.getCache().getExpireAfterAccess())
                .initialCapacity(properties.getCache().getInitialCapacity())
                .build();
    }

    /**
     * Creates Lua script for sliding window algorithm
     */
    private DefaultRedisScript<Long> createSlidingWindowScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("""
            local key = KEYS[1]
            local window = tonumber(ARGV[1])
            local permits = tonumber(ARGV[2])
            local current_time = tonumber(ARGV[3])
            
            -- Remove expired entries
            redis.call('zremrangebyscore', key, 0, current_time - window * 1000)
            
            -- Get current count
            local current_count = redis.call('zcard', key)
            
            if current_count < permits then
                -- Add current request
                redis.call('zadd', key, current_time, current_time)
                redis.call('expire', key, window + 1)
                return 1
            else
                return 0
            end
            """);
        script.setResultType(Long.class);
        return script;
    }

    /**
     * Creates Lua script for fixed window algorithm
     */
    private DefaultRedisScript<Long> createFixedWindowScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("""
            local key = KEYS[1]
            local window = tonumber(ARGV[1])
            local permits = tonumber(ARGV[2])
            local current_time = tonumber(ARGV[3])
            
            local count_key = key .. ':count'
            local time_key = key .. ':time'
            
            -- Get last reset time
            local last_reset = redis.call('get', time_key)
            local current_window = math.floor(current_time / (window * 1000))
            local last_window = 0
            
            if last_reset then
                last_window = math.floor(tonumber(last_reset) / (window * 1000))
            end
            
            if current_window > last_window then
                -- New window, reset counter
                redis.call('set', count_key, '1')
                redis.call('set', time_key, current_time)
                redis.call('expire', count_key, window + 1)
                redis.call('expire', time_key, window + 1)
                return 1
            else
                -- Same window, increment counter
                local current_count = redis.call('incr', count_key)
                redis.call('expire', count_key, window + 1)
                return current_count
            end
            """);
        script.setResultType(Long.class);
        return script;
    }

    /**
     * Creates Lua script for token bucket algorithm
     */
    private DefaultRedisScript<Long> createTokenBucketScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("""
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local current_time = tonumber(ARGV[3])
            
            local bucket_key = key .. ':bucket'
            local time_key = key .. ':time'
            
            -- Get current tokens and last update time
            local tokens = redis.call('get', bucket_key)
            local last_update = redis.call('get', time_key)
            
            if not tokens then
                tokens = capacity
            else
                tokens = tonumber(tokens)
            end
            
            if not last_update then
                last_update = current_time
            else
                last_update = tonumber(last_update)
            end
            
            -- Calculate tokens to add
            local time_diff = (current_time - last_update) / 1000.0
            local tokens_to_add = time_diff * refill_rate
            tokens = math.min(capacity, tokens + tokens_to_add)
            
            if tokens >= 1 then
                -- Consume one token
                tokens = tokens - 1
                redis.call('set', bucket_key, tokens)
                redis.call('set', time_key, current_time)
                redis.call('expire', bucket_key, 3600)
                redis.call('expire', time_key, 3600)
                return math.floor(tokens)
            else
                return -1
            end
            """);
        script.setResultType(Long.class);
        return script;
    }

    /**
     * Creates Lua script for leaky bucket algorithm
     */
    private DefaultRedisScript<Long> createLeakyBucketScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("""
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local leak_rate = tonumber(ARGV[2])
            local current_time = tonumber(ARGV[3])
            
            local bucket_key = key .. ':bucket'
            local time_key = key .. ':time'
            
            -- Get current water level and last update time
            local water = redis.call('get', bucket_key)
            local last_update = redis.call('get', time_key)
            
            if not water then
                water = 0
            else
                water = tonumber(water)
            end
            
            if not last_update then
                last_update = current_time
            else
                last_update = tonumber(last_update)
            end
            
            -- Calculate water to leak
            local time_diff = (current_time - last_update) / 1000.0
            local water_to_leak = time_diff * leak_rate
            water = math.max(0, water - water_to_leak)
            
            if water < capacity then
                -- Add new request
                water = water + 1
                redis.call('set', bucket_key, water)
                redis.call('set', time_key, current_time)
                redis.call('expire', bucket_key, 3600)
                redis.call('expire', time_key, 3600)
                return math.floor(water)
            else
                return -1
            end
            """);
        script.setResultType(Long.class);
        return script;
    }
}
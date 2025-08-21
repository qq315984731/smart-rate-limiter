package io.github.rateLimiter.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.rateLimiter.annotation.RateLimit;
import io.github.rateLimiter.config.RateLimiterProperties;
import io.github.rateLimiter.model.RateLimitContext;
import io.github.rateLimiter.model.RateLimitResult;
import io.github.rateLimiter.service.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Memory-based implementation of RateLimitService
 * 
 * <p>Uses local memory for rate limiting state. Suitable for single-instance
 * deployments or when Redis is not available. Not suitable for distributed
 * applications where rate limits need to be shared across instances.
 * 
 * @author Smart Rate Limiter Team
 * @since 1.0.0
 */
public class MemoryRateLimitService implements RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(MemoryRateLimitService.class);

    private final RateLimiterProperties properties;
    private final Cache<String, RateLimitState> rateLimitCache;
    private final ConcurrentHashMap<String, ReentrantLock> keyLocks;

    public MemoryRateLimitService(RateLimiterProperties properties) {
        this.properties = properties;
        this.rateLimitCache = buildRateLimitCache();
        this.keyLocks = new ConcurrentHashMap<>();
    }

    @Override
    public RateLimitResult checkRateLimit(RateLimitContext context) {
        String key = context.getKey();
        ReentrantLock lock = keyLocks.computeIfAbsent(key, k -> new ReentrantLock());
        
        lock.lock();
        try {
            return executeRateLimitCheck(context);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public RateLimitResult getRateLimitStatus(RateLimitContext context) {
        String key = context.getKey();
        RateLimitState state = rateLimitCache.getIfPresent(key);
        
        if (state == null) {
            return RateLimitResult.builder()
                    .allowed(true)
                    .key(key)
                    .remainingPermits(context.getPermits())
                    .totalPermits(context.getPermits())
                    .algorithm(context.getAlgorithm().name())
                    .dimension(context.getDimension().name())
                    .build();
        }

        long currentTime = System.currentTimeMillis();
        switch (context.getAlgorithm()) {
            case SLIDING_WINDOW:
                return getSlidingWindowStatus(context, state, currentTime);
            case FIXED_WINDOW:
                return getFixedWindowStatus(context, state, currentTime);
            case TOKEN_BUCKET:
                return getTokenBucketStatus(context, state, currentTime);
            case LEAKY_BUCKET:
                return getLeakyBucketStatus(context, state, currentTime);
            default:
                throw new IllegalArgumentException("Unsupported algorithm: " + context.getAlgorithm());
        }
    }

    @Override
    public boolean resetRateLimit(String key) {
        rateLimitCache.invalidate(key);
        keyLocks.remove(key);
        return true;
    }

    @Override
    public boolean isHealthy() {
        return true; // Memory-based service is always healthy
    }

    @Override
    public String getStorageType() {
        return "memory";
    }

    /**
     * Executes the rate limit check based on algorithm
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
     * Sliding window algorithm implementation
     */
    private RateLimitResult executeSlidingWindowCheck(RateLimitContext context, long currentTime) {
        String key = context.getKey();
        RateLimitState state = rateLimitCache.getIfPresent(key);
        
        if (state == null) {
            state = new RateLimitState(currentTime);
            rateLimitCache.put(key, state);
        }

        // Remove expired requests
        long windowStart = currentTime - (context.getWindowSeconds() * 1000L);
        state.requests.removeIf(timestamp -> timestamp < windowStart);

        if (state.requests.size() < context.getPermits()) {
            state.requests.add(currentTime);
            state.lastAccess = currentTime;
            
            return RateLimitResult.builder()
                    .allowed(true)
                    .key(key)
                    .remainingPermits(context.getPermits() - state.requests.size())
                    .totalPermits(context.getPermits())
                    .resetTime(Instant.ofEpochMilli(currentTime + (context.getWindowSeconds() * 1000L)))
                    .algorithm(context.getAlgorithm().name())
                    .dimension(context.getDimension().name())
                    .build();
        } else {
            return RateLimitResult.builder()
                    .allowed(false)
                    .key(key)
                    .remainingPermits(0)
                    .totalPermits(context.getPermits())
                    .resetTime(Instant.ofEpochMilli(currentTime + (context.getWindowSeconds() * 1000L)))
                    .retryAfterSeconds((long) context.getWindowSeconds())
                    .algorithm(context.getAlgorithm().name())
                    .dimension(context.getDimension().name())
                    .build();
        }
    }

    /**
     * Fixed window algorithm implementation
     */
    private RateLimitResult executeFixedWindowCheck(RateLimitContext context, long currentTime) {
        String key = context.getKey();
        RateLimitState state = rateLimitCache.getIfPresent(key);
        
        long windowSize = context.getWindowSeconds() * 1000L;
        long currentWindow = currentTime / windowSize;
        
        if (state == null || state.windowId != currentWindow) {
            state = new RateLimitState(currentTime);
            state.windowId = currentWindow;
            state.count = 1;
            rateLimitCache.put(key, state);
            
            return RateLimitResult.builder()
                    .allowed(true)
                    .key(key)
                    .remainingPermits(context.getPermits() - 1)
                    .totalPermits(context.getPermits())
                    .resetTime(Instant.ofEpochMilli((currentWindow + 1) * windowSize))
                    .algorithm(context.getAlgorithm().name())
                    .dimension(context.getDimension().name())
                    .build();
        }

        if (state.count < context.getPermits()) {
            state.count++;
            state.lastAccess = currentTime;
            
            return RateLimitResult.builder()
                    .allowed(true)
                    .key(key)
                    .remainingPermits(context.getPermits() - state.count)
                    .totalPermits(context.getPermits())
                    .resetTime(Instant.ofEpochMilli((currentWindow + 1) * windowSize))
                    .algorithm(context.getAlgorithm().name())
                    .dimension(context.getDimension().name())
                    .build();
        } else {
            long nextWindowStart = (currentWindow + 1) * windowSize;
            return RateLimitResult.builder()
                    .allowed(false)
                    .key(key)
                    .remainingPermits(0)
                    .totalPermits(context.getPermits())
                    .resetTime(Instant.ofEpochMilli(nextWindowStart))
                    .retryAfterSeconds((nextWindowStart - currentTime) / 1000)
                    .algorithm(context.getAlgorithm().name())
                    .dimension(context.getDimension().name())
                    .build();
        }
    }

    /**
     * Token bucket algorithm implementation
     */
    private RateLimitResult executeTokenBucketCheck(RateLimitContext context, long currentTime) {
        String key = context.getKey();
        RateLimitState state = rateLimitCache.getIfPresent(key);
        
        int bucketCapacity = context.getBucketCapacity() > 0 ? context.getBucketCapacity() : context.getPermits();
        double refillRate = context.getRefillRate() > 0 ? context.getRefillRate() : 
                (double) context.getPermits() / context.getWindowSeconds();

        if (state == null) {
            state = new RateLimitState(currentTime);
            state.tokens = bucketCapacity - 1; // Consume one token for current request
            rateLimitCache.put(key, state);
            
            return RateLimitResult.builder()
                    .allowed(true)
                    .key(key)
                    .remainingPermits((long) state.tokens)
                    .totalPermits(bucketCapacity)
                    .retryAfterSeconds(null)
                    .algorithm(context.getAlgorithm().name())
                    .dimension(context.getDimension().name())
                    .build();
        }

        // Refill tokens
        double timeDiff = (currentTime - state.lastAccess) / 1000.0;
        double tokensToAdd = timeDiff * refillRate;
        state.tokens = Math.min(bucketCapacity, state.tokens + tokensToAdd);

        if (state.tokens >= 1) {
            state.tokens -= 1;
            state.lastAccess = currentTime;
            
            return RateLimitResult.builder()
                    .allowed(true)
                    .key(key)
                    .remainingPermits((long) state.tokens)
                    .totalPermits(bucketCapacity)
                    .algorithm(context.getAlgorithm().name())
                    .dimension(context.getDimension().name())
                    .build();
        } else {
            return RateLimitResult.builder()
                    .allowed(false)
                    .key(key)
                    .remainingPermits(0)
                    .totalPermits(bucketCapacity)
                    .retryAfterSeconds((long) (1.0 / refillRate))
                    .algorithm(context.getAlgorithm().name())
                    .dimension(context.getDimension().name())
                    .build();
        }
    }

    /**
     * Leaky bucket algorithm implementation
     */
    private RateLimitResult executeLeakyBucketCheck(RateLimitContext context, long currentTime) {
        String key = context.getKey();
        RateLimitState state = rateLimitCache.getIfPresent(key);
        
        double leakRate = (double) context.getPermits() / context.getWindowSeconds();

        if (state == null) {
            state = new RateLimitState(currentTime);
            state.water = 1; // Add current request
            rateLimitCache.put(key, state);
            
            return RateLimitResult.builder()
                    .allowed(true)
                    .key(key)
                    .remainingPermits(context.getPermits() - 1)
                    .totalPermits(context.getPermits())
                    .algorithm(context.getAlgorithm().name())
                    .dimension(context.getDimension().name())
                    .build();
        }

        // Leak water
        double timeDiff = (currentTime - state.lastAccess) / 1000.0;
        double waterToLeak = timeDiff * leakRate;
        state.water = Math.max(0, state.water - waterToLeak);

        if (state.water < context.getPermits()) {
            state.water += 1;
            state.lastAccess = currentTime;
            
            return RateLimitResult.builder()
                    .allowed(true)
                    .key(key)
                    .remainingPermits((long) Math.max(0, context.getPermits() - state.water))
                    .totalPermits(context.getPermits())
                    .algorithm(context.getAlgorithm().name())
                    .dimension(context.getDimension().name())
                    .build();
        } else {
            return RateLimitResult.builder()
                    .allowed(false)
                    .key(key)
                    .remainingPermits(0)
                    .totalPermits(context.getPermits())
                    .retryAfterSeconds((long) (1.0 / leakRate))
                    .algorithm(context.getAlgorithm().name())
                    .dimension(context.getDimension().name())
                    .build();
        }
    }

    // Status check methods (simplified versions that don't consume permits)
    
    private RateLimitResult getSlidingWindowStatus(RateLimitContext context, RateLimitState state, long currentTime) {
        long windowStart = currentTime - (context.getWindowSeconds() * 1000L);
        long currentCount = state.requests.stream().mapToLong(Long::longValue).filter(t -> t >= windowStart).count();
        
        return RateLimitResult.builder()
                .allowed(currentCount < context.getPermits())
                .key(context.getKey())
                .remainingPermits(Math.max(0, context.getPermits() - currentCount))
                .totalPermits(context.getPermits())
                .algorithm(context.getAlgorithm().name())
                .dimension(context.getDimension().name())
                .build();
    }

    private RateLimitResult getFixedWindowStatus(RateLimitContext context, RateLimitState state, long currentTime) {
        long windowSize = context.getWindowSeconds() * 1000L;
        long currentWindow = currentTime / windowSize;
        
        if (state.windowId != currentWindow) {
            return RateLimitResult.builder()
                    .allowed(true)
                    .key(context.getKey())
                    .remainingPermits(context.getPermits())
                    .totalPermits(context.getPermits())
                    .algorithm(context.getAlgorithm().name())
                    .dimension(context.getDimension().name())
                    .build();
        }
        
        return RateLimitResult.builder()
                .allowed(state.count < context.getPermits())
                .key(context.getKey())
                .remainingPermits(Math.max(0, context.getPermits() - state.count))
                .totalPermits(context.getPermits())
                .algorithm(context.getAlgorithm().name())
                .dimension(context.getDimension().name())
                .build();
    }

    private RateLimitResult getTokenBucketStatus(RateLimitContext context, RateLimitState state, long currentTime) {
        int bucketCapacity = context.getBucketCapacity() > 0 ? context.getBucketCapacity() : context.getPermits();
        
        return RateLimitResult.builder()
                .allowed(state.tokens >= 1)
                .key(context.getKey())
                .remainingPermits((long) state.tokens)
                .totalPermits(bucketCapacity)
                .algorithm(context.getAlgorithm().name())
                .dimension(context.getDimension().name())
                .build();
    }

    private RateLimitResult getLeakyBucketStatus(RateLimitContext context, RateLimitState state, long currentTime) {
        return RateLimitResult.builder()
                .allowed(state.water < context.getPermits())
                .key(context.getKey())
                .remainingPermits((long) Math.max(0, context.getPermits() - state.water))
                .totalPermits(context.getPermits())
                .algorithm(context.getAlgorithm().name())
                .dimension(context.getDimension().name())
                .build();
    }

    /**
     * Builds the rate limit cache
     */
    private Cache<String, RateLimitState> buildRateLimitCache() {
        return Caffeine.newBuilder()
                .maximumSize(properties.getMemory().getMaxSize())
                .expireAfterAccess(properties.getMemory().getExpireAfterAccess())
                .build();
    }

    /**
     * Internal state for rate limit tracking
     */
    private static class RateLimitState {
        long lastAccess;
        long windowId;
        long count;
        double tokens;
        double water;
        java.util.List<Long> requests = new java.util.concurrent.CopyOnWriteArrayList<>();

        RateLimitState(long currentTime) {
            this.lastAccess = currentTime;
        }
    }
}
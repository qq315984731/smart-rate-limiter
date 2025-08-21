package io.github.rateLimiter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for Smart Rate Limiter
 * 
 * <p>All configurations are under the {@code smart.rate-limiter} prefix.
 * 
 * <h3>Basic Configuration:</h3>
 * <pre>{@code
 * smart:
 *   rate-limiter:
 *     enabled: true
 *     storage-type: redis
 *     key-prefix: "rate_limit:"
 * }</pre>
 * 
 * <h3>Advanced Configuration:</h3>
 * <pre>{@code
 * smart:
 *   rate-limiter:
 *     enabled: true
 *     storage-type: redis
 *     cache:
 *       enabled: true
 *       max-size: 10000
 *       expire-after-write: PT1M
 *     redis:
 *       key-prefix: "app:rate_limit:"
 *       script-cache-size: 100
 *     fallback:
 *       on-error: allow
 *       on-redis-unavailable: memory
 * }</pre>
 * 
 * @author Smart Rate Limiter Team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "smart.rate-limiter")
public class RateLimiterProperties {

    /**
     * Whether rate limiting is enabled globally
     */
    private boolean enabled = true;

    /**
     * Storage type for rate limiting data
     */
    private StorageType storageType = StorageType.REDIS;

    /**
     * Default algorithm to use when not specified in annotation
     */
    private DefaultAlgorithm defaultAlgorithm = DefaultAlgorithm.SLIDING_WINDOW;

    /**
     * Global conflict resolution strategy for GLOBAL dimension
     */
    private ConflictStrategy globalConflictStrategy = ConflictStrategy.MOST_RESTRICTIVE;

    /**
     * Whether to include method signature in rate limit keys
     */
    private boolean includeMethodSignature = true;

    /**
     * Whether to include HTTP method in rate limit keys
     */
    private boolean includeHttpMethod = false;

    @NestedConfigurationProperty
    private Cache cache = new Cache();

    @NestedConfigurationProperty
    private Redis redis = new Redis();

    @NestedConfigurationProperty
    private Memory memory = new Memory();

    @NestedConfigurationProperty
    private Fallback fallback = new Fallback();

    @NestedConfigurationProperty
    private Monitoring monitoring = new Monitoring();

    /**
     * Custom user ID resolver bean name
     * If not specified, will try to auto-detect from Security Context
     */
    private String userIdResolver;

    /**
     * Custom IP resolver bean name
     * If not specified, uses default IP resolution logic
     */
    private String ipResolver;

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public StorageType getStorageType() {
        return storageType;
    }

    public void setStorageType(StorageType storageType) {
        this.storageType = storageType;
    }

    public DefaultAlgorithm getDefaultAlgorithm() {
        return defaultAlgorithm;
    }

    public void setDefaultAlgorithm(DefaultAlgorithm defaultAlgorithm) {
        this.defaultAlgorithm = defaultAlgorithm;
    }

    public ConflictStrategy getGlobalConflictStrategy() {
        return globalConflictStrategy;
    }

    public void setGlobalConflictStrategy(ConflictStrategy globalConflictStrategy) {
        this.globalConflictStrategy = globalConflictStrategy;
    }

    public boolean isIncludeMethodSignature() {
        return includeMethodSignature;
    }

    public void setIncludeMethodSignature(boolean includeMethodSignature) {
        this.includeMethodSignature = includeMethodSignature;
    }

    public boolean isIncludeHttpMethod() {
        return includeHttpMethod;
    }

    public void setIncludeHttpMethod(boolean includeHttpMethod) {
        this.includeHttpMethod = includeHttpMethod;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public Redis getRedis() {
        return redis;
    }

    public void setRedis(Redis redis) {
        this.redis = redis;
    }

    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    public Fallback getFallback() {
        return fallback;
    }

    public void setFallback(Fallback fallback) {
        this.fallback = fallback;
    }

    public Monitoring getMonitoring() {
        return monitoring;
    }

    public void setMonitoring(Monitoring monitoring) {
        this.monitoring = monitoring;
    }

    public String getUserIdResolver() {
        return userIdResolver;
    }

    public void setUserIdResolver(String userIdResolver) {
        this.userIdResolver = userIdResolver;
    }

    public String getIpResolver() {
        return ipResolver;
    }

    public void setIpResolver(String ipResolver) {
        this.ipResolver = ipResolver;
    }

    /**
     * Storage type options
     */
    public enum StorageType {
        /**
         * Use Redis for distributed rate limiting
         */
        REDIS,
        
        /**
         * Use in-memory storage (single instance only)
         */
        MEMORY,
        
        /**
         * Hybrid: Redis primary, memory fallback
         */
        HYBRID
    }

    /**
     * Default algorithm when not specified
     */
    public enum DefaultAlgorithm {
        SLIDING_WINDOW,
        FIXED_WINDOW,
        TOKEN_BUCKET,
        LEAKY_BUCKET
    }

    /**
     * Conflict resolution strategy for GLOBAL dimension
     */
    public enum ConflictStrategy {
        /**
         * Use the most restrictive configuration
         */
        MOST_RESTRICTIVE,
        
        /**
         * Use the least restrictive configuration
         */
        LEAST_RESTRICTIVE,
        
        /**
         * Use the first discovered configuration
         */
        FIRST_DISCOVERED,
        
        /**
         * Fail fast on conflicting configurations
         */
        FAIL_FAST
    }

    /**
     * Local cache configuration
     */
    public static class Cache {
        /**
         * Whether to enable local caching
         */
        private boolean enabled = true;

        /**
         * Maximum cache size
         */
        private long maxSize = 10000L;

        /**
         * Cache expiration time after write
         */
        private Duration expireAfterWrite = Duration.ofMinutes(1);

        /**
         * Cache expiration time after access
         */
        private Duration expireAfterAccess = Duration.ofMinutes(5);

        /**
         * Initial cache capacity
         */
        private int initialCapacity = 1000;

        // Getters and Setters

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }

        public Duration getExpireAfterWrite() {
            return expireAfterWrite;
        }

        public void setExpireAfterWrite(Duration expireAfterWrite) {
            this.expireAfterWrite = expireAfterWrite;
        }

        public Duration getExpireAfterAccess() {
            return expireAfterAccess;
        }

        public void setExpireAfterAccess(Duration expireAfterAccess) {
            this.expireAfterAccess = expireAfterAccess;
        }

        public int getInitialCapacity() {
            return initialCapacity;
        }

        public void setInitialCapacity(int initialCapacity) {
            this.initialCapacity = initialCapacity;
        }
    }

    /**
     * Redis-specific configuration
     */
    public static class Redis {
        /**
         * Key prefix for all rate limit keys
         */
        private String keyPrefix = "smart:rate_limit:";

        /**
         * Key separator
         */
        private String keySeparator = ":";

        /**
         * Lua script cache size
         */
        private int scriptCacheSize = 100;

        /**
         * Connection timeout
         */
        private Duration timeout = Duration.ofSeconds(1);

        /**
         * Whether to use Redis Lua scripts for atomic operations
         */
        private boolean useLuaScripts = true;

        /**
         * Redis database index
         */
        private int database = 0;

        // Getters and Setters

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public String getKeySeparator() {
            return keySeparator;
        }

        public void setKeySeparator(String keySeparator) {
            this.keySeparator = keySeparator;
        }

        public int getScriptCacheSize() {
            return scriptCacheSize;
        }

        public void setScriptCacheSize(int scriptCacheSize) {
            this.scriptCacheSize = scriptCacheSize;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public boolean isUseLuaScripts() {
            return useLuaScripts;
        }

        public void setUseLuaScripts(boolean useLuaScripts) {
            this.useLuaScripts = useLuaScripts;
        }

        public int getDatabase() {
            return database;
        }

        public void setDatabase(int database) {
            this.database = database;
        }
    }

    /**
     * In-memory storage configuration
     */
    public static class Memory {
        /**
         * Maximum number of rate limit records to keep in memory
         */
        private long maxSize = 100000L;

        /**
         * How long to keep unused rate limit records
         */
        private Duration expireAfterAccess = Duration.ofMinutes(10);

        /**
         * Cleanup interval for expired records
         */
        private Duration cleanupInterval = Duration.ofMinutes(1);

        // Getters and Setters

        public long getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }

        public Duration getExpireAfterAccess() {
            return expireAfterAccess;
        }

        public void setExpireAfterAccess(Duration expireAfterAccess) {
            this.expireAfterAccess = expireAfterAccess;
        }

        public Duration getCleanupInterval() {
            return cleanupInterval;
        }

        public void setCleanupInterval(Duration cleanupInterval) {
            this.cleanupInterval = cleanupInterval;
        }
    }

    /**
     * Fallback behavior configuration
     */
    public static class Fallback {
        /**
         * Behavior when rate limiting encounters errors
         */
        private ErrorBehavior onError = ErrorBehavior.ALLOW;

        /**
         * Behavior when Redis is unavailable (for Redis storage type)
         */
        private FallbackStorage onRedisUnavailable = FallbackStorage.MEMORY;

        /**
         * Maximum errors before switching to fallback mode
         */
        private int maxErrors = 5;

        /**
         * Recovery check interval
         */
        private Duration recoveryInterval = Duration.ofMinutes(1);

        // Getters and Setters

        public ErrorBehavior getOnError() {
            return onError;
        }

        public void setOnError(ErrorBehavior onError) {
            this.onError = onError;
        }

        public FallbackStorage getOnRedisUnavailable() {
            return onRedisUnavailable;
        }

        public void setOnRedisUnavailable(FallbackStorage onRedisUnavailable) {
            this.onRedisUnavailable = onRedisUnavailable;
        }

        public int getMaxErrors() {
            return maxErrors;
        }

        public void setMaxErrors(int maxErrors) {
            this.maxErrors = maxErrors;
        }

        public Duration getRecoveryInterval() {
            return recoveryInterval;
        }

        public void setRecoveryInterval(Duration recoveryInterval) {
            this.recoveryInterval = recoveryInterval;
        }

        public enum ErrorBehavior {
            /**
             * Allow the request when error occurs
             */
            ALLOW,
            
            /**
             * Reject the request when error occurs
             */
            REJECT
        }

        public enum FallbackStorage {
            /**
             * Use in-memory storage as fallback
             */
            MEMORY,
            
            /**
             * Allow all requests when primary storage fails
             */
            ALLOW_ALL,
            
            /**
             * Reject all requests when primary storage fails
             */
            REJECT_ALL
        }
    }

    /**
     * Monitoring and metrics configuration
     */
    public static class Monitoring {
        /**
         * Whether to enable metrics collection
         */
        private boolean enabled = false;

        /**
         * Metrics to collect
         */
        private List<MetricType> metrics = new ArrayList<>();

        /**
         * Whether to include detailed tags in metrics
         */
        private boolean includeDetailedTags = false;

        // Getters and Setters

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<MetricType> getMetrics() {
            return metrics;
        }

        public void setMetrics(List<MetricType> metrics) {
            this.metrics = metrics;
        }

        public boolean isIncludeDetailedTags() {
            return includeDetailedTags;
        }

        public void setIncludeDetailedTags(boolean includeDetailedTags) {
            this.includeDetailedTags = includeDetailedTags;
        }

        public enum MetricType {
            /**
             * Total requests counter
             */
            REQUESTS_TOTAL,
            
            /**
             * Allowed requests counter
             */
            REQUESTS_ALLOWED,
            
            /**
             * Rejected requests counter
             */
            REQUESTS_REJECTED,
            
            /**
             * Rate limit check duration
             */
            CHECK_DURATION,
            
            /**
             * Current rate limit state gauge
             */
            RATE_LIMIT_STATE
        }
    }
}
package com.twjgs.ratelimiter.model;

import java.time.Instant;

/**
 * Result of a rate limit check operation
 * 
 * <p>Contains information about whether the request was allowed,
 * current usage statistics, and when the next request would be allowed.
 * 
 * @author Smart Rate Limiter Team
 * @since 1.0.0
 */
public class RateLimitResult {

    private final boolean allowed;
    private final String key;
    private final long remainingPermits;
    private final long totalPermits;
    private final Instant resetTime;
    private final Long retryAfterSeconds;
    private final String algorithm;
    private final String dimension;

    private RateLimitResult(Builder builder) {
        this.allowed = builder.allowed;
        this.key = builder.key;
        this.remainingPermits = builder.remainingPermits;
        this.totalPermits = builder.totalPermits;
        this.resetTime = builder.resetTime;
        this.retryAfterSeconds = builder.retryAfterSeconds;
        this.algorithm = builder.algorithm;
        this.dimension = builder.dimension;
    }

    /**
     * Whether the request was allowed
     * 
     * @return true if allowed, false if rate limited
     */
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * The rate limit key that was checked
     * 
     * @return the rate limit key
     */
    public String getKey() {
        return key;
    }

    /**
     * Number of remaining permits in the current window
     * 
     * @return remaining permits
     */
    public long getRemainingPermits() {
        return remainingPermits;
    }

    /**
     * Total number of permits allowed in the window
     * 
     * @return total permits
     */
    public long getTotalPermits() {
        return totalPermits;
    }

    /**
     * When the rate limit window resets
     * 
     * @return reset time, or null if not applicable
     */
    public Instant getResetTime() {
        return resetTime;
    }

    /**
     * Suggested retry time in seconds
     * 
     * @return retry time in seconds, or null if not applicable
     */
    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    /**
     * The algorithm used for this rate limit check
     * 
     * @return algorithm name
     */
    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * The dimension that was rate limited
     * 
     * @return dimension name
     */
    public String getDimension() {
        return dimension;
    }

    /**
     * Creates a new builder for RateLimitResult
     * 
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates an allowed result
     * 
     * @param key the rate limit key
     * @param remainingPermits remaining permits
     * @param totalPermits total permits
     * @return allowed result
     */
    public static RateLimitResult allowed(String key, long remainingPermits, long totalPermits) {
        return builder()
                .allowed(true)
                .key(key)
                .remainingPermits(remainingPermits)
                .totalPermits(totalPermits)
                .build();
    }

    /**
     * Creates a rejected result
     * 
     * @param key the rate limit key
     * @param totalPermits total permits
     * @param retryAfterSeconds when to retry
     * @return rejected result
     */
    public static RateLimitResult rejected(String key, long totalPermits, Long retryAfterSeconds) {
        return builder()
                .allowed(false)
                .key(key)
                .remainingPermits(0)
                .totalPermits(totalPermits)
                .retryAfterSeconds(retryAfterSeconds)
                .build();
    }

    @Override
    public String toString() {
        return String.format("RateLimitResult{allowed=%s, key='%s', remaining=%d/%d, retryAfter=%s, algorithm='%s', dimension='%s'}",
                allowed, key, remainingPermits, totalPermits, retryAfterSeconds, algorithm, dimension);
    }

    /**
     * Builder for RateLimitResult
     */
    public static class Builder {
        private boolean allowed;
        private String key;
        private long remainingPermits;
        private long totalPermits;
        private Instant resetTime;
        private Long retryAfterSeconds;
        private String algorithm;
        private String dimension;

        public Builder allowed(boolean allowed) {
            this.allowed = allowed;
            return this;
        }

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder remainingPermits(long remainingPermits) {
            this.remainingPermits = remainingPermits;
            return this;
        }

        public Builder totalPermits(long totalPermits) {
            this.totalPermits = totalPermits;
            return this;
        }

        public Builder resetTime(Instant resetTime) {
            this.resetTime = resetTime;
            return this;
        }

        public Builder retryAfterSeconds(Long retryAfterSeconds) {
            this.retryAfterSeconds = retryAfterSeconds;
            return this;
        }

        public Builder algorithm(String algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        public Builder dimension(String dimension) {
            this.dimension = dimension;
            return this;
        }

        public RateLimitResult build() {
            return new RateLimitResult(this);
        }
    }
}
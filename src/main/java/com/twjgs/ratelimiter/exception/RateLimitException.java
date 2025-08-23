package com.twjgs.ratelimiter.exception;

/**
 * Exception thrown when a rate limit is exceeded
 * 
 * <p>This exception contains information about the rate limit that was exceeded,
 * including retry information and the specific limit configuration.
 * 
 * @author Smart Rate Limiter Team
 * @since 1.0.0
 */
public class RateLimitException extends ApiProtectionException {

    private final String limitKey;
    private final int permits;
    private final int windowSeconds;
    private final String algorithm;
    private final String dimension;
    private final Long retryAfterSeconds;

    /**
     * Creates a new RateLimitException
     * 
     * @param message the error message
     */
    public RateLimitException(String message) {
        super(message, "RATE_LIMIT_EXCEEDED");
        this.limitKey = null;
        this.permits = -1;
        this.windowSeconds = -1;
        this.algorithm = null;
        this.dimension = null;
        this.retryAfterSeconds = null;
    }

    /**
     * Creates a new RateLimitException with cause
     * 
     * @param message the error message
     * @param cause the underlying cause
     */
    public RateLimitException(String message, Throwable cause) {
        super(message, "RATE_LIMIT_EXCEEDED", cause);
        this.limitKey = null;
        this.permits = -1;
        this.windowSeconds = -1;
        this.algorithm = null;
        this.dimension = null;
        this.retryAfterSeconds = null;
    }

    /**
     * Creates a new RateLimitException with detailed rate limit information
     * 
     * @param message the error message
     * @param limitKey the rate limit key that was exceeded
     * @param permits the number of permits allowed
     * @param windowSeconds the time window in seconds
     * @param algorithm the rate limiting algorithm used
     * @param dimension the rate limiting dimension
     * @param retryAfterSeconds suggested retry time in seconds
     */
    public RateLimitException(String message, 
                             String limitKey, 
                             int permits, 
                             int windowSeconds, 
                             String algorithm, 
                             String dimension, 
                             Long retryAfterSeconds) {
        super(message, "RATE_LIMIT_EXCEEDED");
        this.limitKey = limitKey;
        this.permits = permits;
        this.windowSeconds = windowSeconds;
        this.algorithm = algorithm;
        this.dimension = dimension;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Gets the rate limit key that was exceeded
     * 
     * @return the limit key, or null if not available
     */
    public String getLimitKey() {
        return limitKey;
    }

    /**
     * Gets the number of permits allowed in the rate limit
     * 
     * @return the number of permits, or -1 if not available
     */
    public int getPermits() {
        return permits;
    }

    /**
     * Gets the time window in seconds for the rate limit
     * 
     * @return the window duration, or -1 if not available
     */
    public int getWindowSeconds() {
        return windowSeconds;
    }

    /**
     * Gets the rate limiting algorithm that was used
     * 
     * @return the algorithm name, or null if not available
     */
    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * Gets the rate limiting dimension that was applied
     * 
     * @return the dimension name, or null if not available
     */
    public String getDimension() {
        return dimension;
    }

    /**
     * Gets the suggested retry time in seconds
     * 
     * @return retry time in seconds, or null if not available
     */
    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    /**
     * Checks if detailed rate limit information is available
     * 
     * @return true if detailed information is available
     */
    public boolean hasDetailedInfo() {
        return limitKey != null && permits > 0 && windowSeconds > 0;
    }

    @Override
    public String toString() {
        if (hasDetailedInfo()) {
            return String.format("RateLimitException: %s [key=%s, limit=%d/%ds, algorithm=%s, dimension=%s, retryAfter=%s]",
                    getMessage(), limitKey, permits, windowSeconds, algorithm, dimension, retryAfterSeconds);
        } else {
            return String.format("RateLimitException: %s", getMessage());
        }
    }
    
    @Override
    public String getErrorType() {
        return "RATE_LIMIT";
    }
    
    @Override
    public int getHttpStatus() {
        return 429; // Too Many Requests
    }
    
    @Override
    public boolean shouldLogError() {
        // 限流异常通常不需要记录错误日志，只需要记录警告
        return false;
    }
}
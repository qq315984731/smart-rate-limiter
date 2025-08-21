package io.github.rateLimiter.model;

import io.github.rateLimiter.annotation.RateLimit;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Context information for rate limiting operations
 * 
 * <p>Contains all necessary information for performing rate limit checks,
 * including request details, user information, and rate limit configuration.
 * 
 * @author Smart Rate Limiter Team
 * @since 1.0.0
 */
public class RateLimitContext {

    private final String key;
    private final int permits;
    private final int windowSeconds;
    private final RateLimit.LimitAlgorithm algorithm;
    private final RateLimit.LimitDimension dimension;
    private final RateLimit.LimitStrategy strategy;
    private final String methodSignature;
    private final String userId;
    private final String clientIp;
    private final HttpServletRequest request;
    private final long queueTimeout;
    private final String customMessage;
    private final int bucketCapacity;
    private final double refillRate;

    private RateLimitContext(Builder builder) {
        this.key = builder.key;
        this.permits = builder.permits;
        this.windowSeconds = builder.windowSeconds;
        this.algorithm = builder.algorithm;
        this.dimension = builder.dimension;
        this.strategy = builder.strategy;
        this.methodSignature = builder.methodSignature;
        this.userId = builder.userId;
        this.clientIp = builder.clientIp;
        this.request = builder.request;
        this.queueTimeout = builder.queueTimeout;
        this.customMessage = builder.customMessage;
        this.bucketCapacity = builder.bucketCapacity;
        this.refillRate = builder.refillRate;
    }

    // Getters

    public String getKey() {
        return key;
    }

    public int getPermits() {
        return permits;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public RateLimit.LimitAlgorithm getAlgorithm() {
        return algorithm;
    }

    public RateLimit.LimitDimension getDimension() {
        return dimension;
    }

    public RateLimit.LimitStrategy getStrategy() {
        return strategy;
    }

    public String getMethodSignature() {
        return methodSignature;
    }

    public String getUserId() {
        return userId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public long getQueueTimeout() {
        return queueTimeout;
    }

    public String getCustomMessage() {
        return customMessage;
    }

    public int getBucketCapacity() {
        return bucketCapacity;
    }

    public double getRefillRate() {
        return refillRate;
    }

    /**
     * Creates a new builder for RateLimitContext
     * 
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return String.format("RateLimitContext{key='%s', permits=%d, window=%ds, algorithm=%s, dimension=%s, strategy=%s}",
                key, permits, windowSeconds, algorithm, dimension, strategy);
    }

    /**
     * Builder for RateLimitContext
     */
    public static class Builder {
        private String key;
        private int permits;
        private int windowSeconds;
        private RateLimit.LimitAlgorithm algorithm;
        private RateLimit.LimitDimension dimension;
        private RateLimit.LimitStrategy strategy;
        private String methodSignature;
        private String userId;
        private String clientIp;
        private HttpServletRequest request;
        private long queueTimeout = 1000L;
        private String customMessage;
        private int bucketCapacity = -1;
        private double refillRate = -1.0;

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder permits(int permits) {
            this.permits = permits;
            return this;
        }

        public Builder windowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
            return this;
        }

        public Builder algorithm(RateLimit.LimitAlgorithm algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        public Builder dimension(RateLimit.LimitDimension dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder strategy(RateLimit.LimitStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder methodSignature(String methodSignature) {
            this.methodSignature = methodSignature;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder clientIp(String clientIp) {
            this.clientIp = clientIp;
            return this;
        }

        public Builder request(HttpServletRequest request) {
            this.request = request;
            return this;
        }

        public Builder queueTimeout(long queueTimeout) {
            this.queueTimeout = queueTimeout;
            return this;
        }

        public Builder customMessage(String customMessage) {
            this.customMessage = customMessage;
            return this;
        }

        public Builder bucketCapacity(int bucketCapacity) {
            this.bucketCapacity = bucketCapacity;
            return this;
        }

        public Builder refillRate(double refillRate) {
            this.refillRate = refillRate;
            return this;
        }

        public RateLimitContext build() {
            return new RateLimitContext(this);
        }
    }
}
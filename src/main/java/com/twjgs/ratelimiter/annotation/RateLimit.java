package com.twjgs.ratelimiter.annotation;

import java.lang.annotation.*;

/**
 * Rate limiting annotation for controlling request rates at method level
 * 
 * <p>Supports multiple limiting dimensions, algorithms, and strategies.
 * Can be used alone or combined with {@link MultiRateLimit} for complex scenarios.
 * 
 * <h3>Basic Usage:</h3>
 * <pre>{@code
 * @RateLimit(permits = 10, window = 60)
 * public void myMethod() {
 *     // This method allows max 10 calls per 60 seconds globally
 * }
 * }</pre>
 * 
 * <h3>Advanced Usage:</h3>
 * <pre>{@code
 * @RateLimit(
 *     dimension = LimitDimension.USER,
 *     permits = 5, 
 *     window = 10,
 *     algorithm = LimitAlgorithm.TOKEN_BUCKET,
 *     strategy = LimitStrategy.QUEUE,
 *     message = "Too many requests, please wait"
 * )
 * public void sensitiveOperation() {
 *     // Per-user limiting with token bucket and queue strategy
 * }
 * }</pre>
 * 
 * @author Smart Rate Limiter Team
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * Whether the rate limit is enabled
     * 
     * @return true if enabled, false otherwise
     */
    boolean enabled() default true;

    /**
     * Rate limiting dimension
     * 
     * @return the limiting dimension
     */
    LimitDimension dimension() default LimitDimension.IP;

    /**
     * Number of permits allowed within the time window
     * 
     * @return number of permitted requests
     */
    int permits() default 10;

    /**
     * Time window in seconds
     * 
     * @return time window duration
     */
    int window() default 60;

    /**
     * Rate limiting algorithm to use
     * 
     * @return the algorithm type
     */
    LimitAlgorithm algorithm() default LimitAlgorithm.SLIDING_WINDOW;

    /**
     * Strategy when rate limit is exceeded
     * 
     * @return the limiting strategy
     */
    LimitStrategy strategy() default LimitStrategy.REJECT;

    /**
     * Error message when rate limit is exceeded
     * 
     * @return custom error message
     */
    String message() default "Request rate limit exceeded, please try again later";

    /**
     * SpEL expression for custom key generation (only used with CUSTOM dimension)
     * 
     * <p>Available variables:
     * <ul>
     *   <li>{@code #request} - HTTP request object</li>
     *   <li>{@code #userId} - Current user ID (if available)</li>
     *   <li>{@code #ip} - Client IP address</li>
     *   <li>{@code #methodName} - Method name</li>
     *   <li>{@code #className} - Class name</li>
     * </ul>
     * 
     * <p>Example: {@code "#userId + ':' + #ip"} for user+IP combination
     * 
     * @return SpEL expression
     */
    String keyExpression() default "";

    /**
     * Token bucket capacity (only used with TOKEN_BUCKET algorithm)
     * 
     * @return bucket capacity, defaults to permits value
     */
    int bucketCapacity() default -1;

    /**
     * Token refill rate per second (only used with TOKEN_BUCKET algorithm)
     * 
     * @return refill rate, defaults to permits/window
     */
    double refillRate() default -1.0;

    /**
     * Queue timeout in milliseconds (only used with QUEUE strategy)
     * 
     * @return timeout duration
     */
    long queueTimeout() default 1000L;

    /**
     * Rate limiting dimensions
     */
    enum LimitDimension {
        /**
         * Per authenticated user
         */
        USER,
        
        /**
         * Per client IP address
         */
        IP,
        
        /**
         * Per API method
         */
        API,
        
        /**
         * Global across all requests
         */
        GLOBAL,
        
        /**
         * Custom key using SpEL expression
         */
        CUSTOM
    }

    /**
     * Rate limiting algorithms
     */
    enum LimitAlgorithm {
        /**
         * Sliding window counter - most accurate, moderate performance
         */
        SLIDING_WINDOW,
        
        /**
         * Fixed window counter - high performance, possible burst at boundaries
         */
        FIXED_WINDOW,
        
        /**
         * Token bucket - allows burst traffic, smooth average rate
         */
        TOKEN_BUCKET,
        
        /**
         * Leaky bucket - strict rate enforcement, traffic shaping
         */
        LEAKY_BUCKET
    }

    /**
     * Strategies when rate limit is exceeded
     */
    enum LimitStrategy {
        /**
         * Immediately reject the request
         */
        REJECT,
        
        /**
         * Queue the request and wait
         */
        QUEUE
    }
}
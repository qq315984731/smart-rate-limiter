package com.twjgs.ratelimiter.annotation;

import java.lang.annotation.*;

/**
 * Multi-dimensional rate limiting annotation
 * 
 * <p>Allows applying multiple rate limiting rules simultaneously to a single method.
 * Useful for complex scenarios like limiting both per-user and per-IP rates.
 * 
 * <h3>Basic Usage:</h3>
 * <pre>{@code
 * @MultiRateLimit({
 *     @RateLimit(dimension = LimitDimension.IP, permits = 100, window = 60),
 *     @RateLimit(dimension = LimitDimension.USER, permits = 10, window = 60)
 * })
 * public void sensitiveOperation() {
 *     // Limited to 100 requests per IP AND 10 requests per user per minute
 * }
 * }</pre>
 * 
 * <h3>Advanced Usage with OR Strategy:</h3>
 * <pre>{@code
 * @MultiRateLimit(
 *     value = {
 *         @RateLimit(dimension = LimitDimension.USER, permits = 10, window = 60),
 *         @RateLimit(dimension = LimitDimension.GLOBAL, permits = 1000, window = 60)
 *     },
 *     strategy = CombineStrategy.OR,
 *     shortCircuit = true,
 *     message = "Rate limit exceeded for this operation"
 * )
 * public void flexibleOperation() {
 *     // Allows if EITHER user limit OR global limit is available
 * }
 * }</pre>
 * 
 * @author Smart Rate Limiter Team
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MultiRateLimit {

    /**
     * Array of rate limiting rules to apply
     * 
     * @return array of RateLimit annotations
     */
    RateLimit[] value();

    /**
     * Strategy for combining multiple rate limits
     * 
     * @return combination strategy
     */
    CombineStrategy strategy() default CombineStrategy.AND;

    /**
     * Whether to use short-circuit evaluation
     * 
     * <p>When enabled:
     * <ul>
     *   <li>AND strategy: Returns false immediately when first limit fails</li>
     *   <li>OR strategy: Returns true immediately when first limit passes</li>
     * </ul>
     * 
     * <p>When disabled, all limits are evaluated for monitoring purposes.
     * 
     * @return true for short-circuit evaluation
     */
    boolean shortCircuit() default true;

    /**
     * Custom error message when any rate limit is exceeded
     * 
     * @return error message
     */
    String message() default "Request rate limit exceeded";

    /**
     * Combination strategies for multiple rate limits
     */
    enum CombineStrategy {
        /**
         * All rate limits must pass (default)
         * Most restrictive - requires all limits to be satisfied
         */
        AND,
        
        /**
         * At least one rate limit must pass
         * Most permissive - allows if any single limit is satisfied
         */
        OR
    }
}
package com.twjgs.ratelimiter.annotation;

import java.lang.annotation.*;

/**
 * Rate limiting annotation for controlling request rates at method level
 * 
 * <p>Supports multiple limiting dimensions, algorithms, and strategies.
 * Can be used alone or combined with {@link MultiRateLimit} for complex scenarios.
 * 
 * <h3>Basic Usage Examples:</h3>
 * <pre>{@code
 * // GET请求 - 基于IP限流
 * @RateLimit(permits = 10, window = 60)
 * @GetMapping("/api/data")
 * public DataResult getData(@RequestParam String type) {
 *     return dataService.query(type);
 * }
 * 
 * // POST请求 - 基于用户限流
 * @RateLimit(
 *     dimension = LimitDimension.USER,
 *     permits = 5, 
 *     window = 60,
 *     message = "操作过于频繁，请稍后再试"
 * )
 * @PostMapping("/api/upload")
 * public UploadResult upload(@RequestBody UploadRequest request) {
 *     return uploadService.process(request);
 * }
 * }</pre>
 * 
 * <h3>Advanced Usage Examples:</h3>
 * <pre>{@code
 * // POST请求 - 自定义维度，基于用户ID+业务类型限流
 * @RateLimit(
 *     dimension = LimitDimension.CUSTOM,
 *     keyExpression = "#userId + ':' + #request.businessType",  // 访问@RequestBody中的字段
 *     permits = 3,
 *     window = 300,
 *     algorithm = LimitAlgorithm.TOKEN_BUCKET,
 *     message = "该业务操作限流中"
 * )
 * @PostMapping("/api/business")
 * public BusinessResult processBusiness(@RequestBody BusinessRequest request) {
 *     return businessService.process(request);
 * }
 * 
 * // POST表单提交 - 基于表单参数的自定义限流
 * @RateLimit(
 *     dimension = LimitDimension.CUSTOM,
 *     keyExpression = "#ip + ':order:' + #orderType",  // 访问@RequestParam参数
 *     permits = 10,
 *     window = 60,
 *     strategy = LimitStrategy.QUEUE,
 *     queueTimeout = 2000L,
 *     message = "订单提交限流中，请稍候"
 * )
 * @PostMapping("/api/order")
 * public OrderResult createOrder(@RequestParam String orderType,
 *                               @RequestParam BigDecimal amount) {
 *     return orderService.create(orderType, amount);
 * }
 * 
 * // 复杂限流策略 - 多维度组合
 * @RateLimit(
 *     dimension = LimitDimension.CUSTOM,
 *     keyExpression = "#request.tenantId + ':api:' + #methodName + ':user:' + #userId",
 *     permits = 100,
 *     window = 3600,  // 每小时100次
 *     algorithm = LimitAlgorithm.SLIDING_WINDOW,
 *     message = "租户API调用频率超限"
 * )
 * @PostMapping("/api/tenant/operation")
 * public TenantResult tenantOperation(@RequestBody TenantRequest request) {
 *     return tenantService.process(request);
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
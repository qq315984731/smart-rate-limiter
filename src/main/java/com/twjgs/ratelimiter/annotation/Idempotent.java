package com.twjgs.ratelimiter.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等性控制注解
 * 
 * <p>通过此注解可以防止同一个请求被重复执行，适用于支付、订单创建等关键业务场景。
 * 
 * <p>工作原理：
 * <ul>
 *   <li>第一次请求：执行业务逻辑，缓存请求键和响应结果</li>
 *   <li>重复请求：直接返回第一次请求的缓存结果，不执行业务逻辑</li>
 *   <li>超时后：缓存失效，允许重新执行</li>
 * </ul>
 * 
 * <h3>使用示例 - 从简单到复杂</h3>
 * 
 * <h4>1. 最简单的使用（仅设置时间）：</h4>
 * <pre>{@code
 * // 5分钟内重复请求返回相同结果，失败后允许重试
 * @Idempotent(timeout = 300)
 * @PostMapping("/api/payment")
 * public PaymentResult createPayment(@RequestBody PaymentRequest request) {
 *     return paymentService.process(request);
 * }
 * }</pre>
 * 
 * <h4>2. 控制重复请求行为：</h4>
 * <pre>{@code
 * // 重复请求时抛出异常，由用户自己处理
 * @Idempotent(
 *     timeout = 600,
 *     returnFirstResult = false,  // 抛出异常而不是返回结果
 *     message = "请勿重复提交订单"
 * )
 * @PostMapping("/api/order")
 * public OrderResult createOrder(@RequestBody OrderRequest request) {
 *     return orderService.create(request);
 * }
 * }</pre>
 * 
 * <h4>3. 控制失败重试行为：</h4>
 * <pre>{@code
 * // 失败后不允许重试
 * @Idempotent(
 *     timeout = 1800,
 *     allowRetryOnFailure = false  // 一旦失败就不允许重试
 * )
 * @PostMapping("/api/critical-operation")
 * public CriticalResult performCritical(@RequestBody CriticalRequest request) {
 *     return criticalService.process(request);
 * }
 * }</pre>
 * 
 * <h4>4. 定制失败检测条件：</h4>
 * <pre>{@code
 * // 只有特定异常才视为"失败"，其他异常不影响幂等性
 * @Idempotent(
 *     timeout = 900,
 *     allowRetryOnFailure = true,
 *     failureDetection = FailureDetection.SPECIFIC_EXCEPTIONS,
 *     failureExceptions = {TimeoutException.class, BusinessException.class}
 * )
 * @PostMapping("/api/external-call")
 * public ExternalResult callExternal(@RequestBody ExternalRequest request) {
 *     return externalService.call(request);
 * }
 * 
 * // 基于HTTP状态码判断失败
 * @Idempotent(
 *     timeout = 600,
 *     failureDetection = FailureDetection.CUSTOM_CONDITION,
 *     failureCondition = "#statusCode >= 500"  // 5xx状态码才视为失败
 * )
 * @PostMapping("/api/http-operation")
 * public HttpResult performHttp(@RequestBody HttpRequest request) {
 *     return httpService.execute(request);
 * }
 * }</pre>
 * 
 * <h4>5. 自定义幂等键（高级用法）：</h4>
 * <pre>{@code
 * // 使用订单号作为幂等键，而不是默认的用户ID+参数
 * @Idempotent(
 *     timeout = 3600,
 *     keyStrategy = KeyStrategy.CUSTOM,
 *     keyExpression = "#request.orderNo"  // 使用订单号作为唯一标识
 * )
 * @PostMapping("/api/order-processing")
 * public ProcessResult processOrder(@RequestBody OrderProcessRequest request) {
 *     return orderProcessor.process(request);
 * }
 * }</pre>
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    
    /**
     * 幂等超时时间（秒）
     * 
     * <p>在此时间内，相同的请求将被认为是重复请求。
     * 超过此时间后，缓存失效，允许重新执行。
     * 
     * @return 超时时间，默认60秒（1分钟）
     */
    int timeout() default 60;
    
    /**
     * 幂等键生成策略
     * 
     * @return 键生成策略，默认使用参数Hash
     */
    KeyStrategy keyStrategy() default KeyStrategy.USER_PARAMS;
    
    /**
     * 自定义键表达式（SpEL表达式）
     * 
     * <p>当keyStrategy为CUSTOM时使用。支持访问方法参数和Spring上下文。
     * 
     * <p>可用变量：
     * <ul>
     *   <li>#request - HTTP请求对象</li>
     *   <li>#方法参数名 - 方法的具体参数</li>
     *   <li>@beanName - Spring容器中的bean</li>
     * </ul>
     * 
     * @return SpEL表达式，默认为空
     */
    String keyExpression() default "";
    
    /**
     * 幂等失败时的提示消息
     * 
     * @return 提示消息，默认为通用提示
     */
    String message() default "操作正在处理中，请勿重复操作";
    
    /**
     * 重复请求时的行为
     * 
     * <p>当检测到重复请求时：</p>
     * <ul>
     *   <li>true: 返回第一次请求的缓存结果（推荐）</li>
     *   <li>false: 抛出IdempotentException异常，由用户处理</li>
     * </ul>
     * 
     * @return 是否返回缓存结果，默认true
     */
    boolean returnFirstResult() default true;
    
    /**
     * 失败后是否允许重试
     * 
     * <p>当之前的请求执行失败时，是否允许新请求重新执行：</p>
     * <ul>
     *   <li>true: 允许重新执行（推荐，适用于大多数场景）</li>
     *   <li>false: 不允许重试，抛出包含失败信息的异常</li>
     * </ul>
     * 
     * @return 是否允许失败重试，默认true
     */
    boolean allowRetryOnFailure() default true;
    
    /**
     * 定义什么情况下认为请求"失败"
     * 
     * <p>当allowRetryOnFailure为true时，只有匹配此条件的失败才允许重试。</p>
     * 
     * <p>可用选项：</p>
     * <ul>
     *   <li>ALL: 所有异常都视为失败（默认）</li>
     *   <li>RUNTIME_EXCEPTION: 仅RuntimeException及子类视为失败</li>
     *   <li>SPECIFIC_EXCEPTIONS: 仅指定的异常类型视为失败（通过failureExceptions指定）</li>
     *   <li>CUSTOM_CONDITION: 自定义失败条件（通过failureCondition SpEL表达式）</li>
     * </ul>
     * 
     * @return 失败检测策略，默认ALL
     */
    FailureDetection failureDetection() default FailureDetection.ALL;
    
    /**
     * 指定哪些异常类型视为"失败"
     * 
     * <p>仅当failureDetection为SPECIFIC_EXCEPTIONS时有效。</p>
     * 
     * <p>示例：</p>
     * <pre>{@code
     * failureExceptions = {TimeoutException.class, BusinessException.class}
     * }</pre>
     * 
     * @return 失败异常类型数组，默认为空
     */
    Class<? extends Throwable>[] failureExceptions() default {};
    
    /**
     * 自定义失败检测条件（SpEL表达式）
     * 
     * <p>仅当failureDetection为CUSTOM_CONDITION时有效。</p>
     * 
     * <p>可用变量：</p>
     * <ul>
     *   <li>#exception - 捕获的异常对象</li>
     *   <li>#response - HTTP响应对象（如果有）</li>
     *   <li>#statusCode - HTTP状态码（如果有）</li>
     * </ul>
     * 
     * <p>示例：</p>
     * <pre>{@code
     * // HTTP状态码不是200才视为失败
     * failureCondition = "#statusCode != 200"
     * 
     * // 特定异常类型才视为失败
     * failureCondition = "#exception instanceof T(java.util.concurrent.TimeoutException)"
     * 
     * // 复合条件
     * failureCondition = "#statusCode >= 500 or #exception.class.simpleName.contains('Timeout')"
     * }</pre>
     * 
     * @return SpEL表达式，默认为空
     */
    String failureCondition() default "";
    
    /**
     * 失败检测策略枚举
     */
    enum FailureDetection {
        /**
         * 所有异常都视为失败
         * 
         * <p>这是最宽松的策略，任何异常都会触发失败处理。</p>
         * <p>适用场景：大多数业务场景，简单易用。</p>
         */
        ALL,
        
        /**
         * 仅RuntimeException及其子类视为失败
         * 
         * <p>检查异常（如IOException）不视为失败，通常表示外部环境问题。</p>
         * <p>适用场景：区分业务异常和系统异常的场景。</p>
         */
        RUNTIME_EXCEPTION,
        
        /**
         * 仅指定的异常类型视为失败
         * 
         * <p>只有failureExceptions中指定的异常类型才视为失败。</p>
         * <p>适用场景：需要精确控制哪些异常允许重试的场景。</p>
         */
        SPECIFIC_EXCEPTIONS,
        
        /**
         * 自定义失败检测条件
         * 
         * <p>使用SpEL表达式自定义失败检测逻辑。</p>
         * <p>适用场景：复杂的失败判断逻辑，如基于HTTP状态码或异常消息。</p>
         */
        CUSTOM_CONDITION
    }
    
    /**
     * 幂等键生成策略枚举
     */
    enum KeyStrategy {
        /**
         * 参数Hash策略
         * 
         * <p>将方法的所有参数进行Hash计算作为幂等键。
         * 适用于大多数场景。
         */
        PARAMS_HASH,
        
        /**
         * 用户+参数策略
         * 
         * <p>将用户ID和方法参数组合Hash作为幂等键。
         * 确保不同用户之间的幂等性独立。
         */
        USER_PARAMS,
        
        /**
         * 自定义策略
         * 
         * <p>使用keyExpression指定的SpEL表达式计算幂等键。
         * 提供最大的灵活性。
         */
        CUSTOM
    }
}
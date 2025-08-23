package com.twjgs.ratelimiter.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 防重复提交注解
 * 
 * <p>通过此注解可以防止用户快速重复点击或提交表单，适用于表单提交、点赞、评论等场景。
 * 
 * <p>工作原理：
 * <ul>
 *   <li>首次请求：记录提交标记，允许请求继续执行</li>
 *   <li>重复请求：在时间间隔内检测到重复提交，拒绝请求</li>
 *   <li>间隔后：标记过期，允许重新提交</li>
 * </ul>
 * 
 * <p>与幂等性控制的区别：
 * <ul>
 *   <li>防重复提交：主要防止用户快速重复操作，通常基于用户+接口维度</li>
 *   <li>幂等性控制：防止相同业务数据重复处理，通常基于业务参数</li>
 * </ul>
 * 
 * <p>使用示例：
 * <pre>{@code
 * // GET请求 - 基础使用，用户+方法维度防重复
 * @DuplicateSubmit(interval = 3)
 * @GetMapping("/api/refresh")
 * public RefreshResult refreshData(@RequestParam String type) {
 *     return refreshService.refresh(type);
 * }
 * 
 * // POST请求 - 基础使用，5秒内防止重复提交
 * @DuplicateSubmit
 * @PostMapping("/api/comment")
 * public CommentResult addComment(@RequestBody CommentRequest request) {
 *     return commentService.add(request);
 * }
 * 
 * // POST请求 - 自定义维度，基于用户ID+特定参数防重复
 * @DuplicateSubmit(
 *     dimension = KeyDimension.CUSTOM,
 *     keyExpression = "#userId + ':article:' + #request.articleId",  // 访问@RequestBody中的字段
 *     interval = 10,
 *     message = "点赞操作过于频繁，请10秒后重试"
 * )
 * @PostMapping("/api/like")
 * public LikeResult addLike(@RequestBody LikeRequest request) {
 *     return likeService.add(request);
 * }
 * 
 * // POST表单提交 - 基于表单参数防重复
 * @DuplicateSubmit(
 *     dimension = KeyDimension.CUSTOM,
 *     keyExpression = "#userId + ':order:' + #orderNo",  // 访问@RequestParam参数
 *     interval = 30,
 *     message = "订单提交过于频繁，请30秒后重试"
 * )
 * @PostMapping("/api/submit-order")
 * public OrderResult submitOrder(@RequestParam String orderNo, 
 *                               @RequestParam BigDecimal amount) {
 *     return orderService.submit(orderNo, amount);
 * }
 * 
 * // 按IP维度防重复 - 适用于匿名用户场景
 * @DuplicateSubmit(
 *     interval = 60,
 *     dimension = KeyDimension.IP_METHOD,
 *     message = "该IP操作过于频繁，请1分钟后重试"
 * )
 * @PostMapping("/api/feedback")
 * public FeedbackResult submitFeedback(@RequestBody FeedbackRequest request) {
 *     return feedbackService.submit(request);
 * }
 * 
 * // 复杂防重复策略 - 组合多个参数和条件
 * @DuplicateSubmit(
 *     dimension = KeyDimension.CUSTOM,
 *     keyExpression = "#request.userId + ':vote:' + #request.targetType + ':' + #request.targetId",
 *     interval = 86400,  // 24小时内同一用户对同一目标只能投票一次
 *     message = "您已经投票过了，24小时内不能重复投票"
 * )
 * @PostMapping("/api/vote")
 * public VoteResult vote(@RequestBody VoteRequest request) {
 *     return voteService.submit(request);
 * }
 * }</pre>
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DuplicateSubmit {
    
    /**
     * 防重复时间间隔（秒）
     * 
     * <p>在此时间间隔内，相同维度的请求将被认为是重复提交。
     * 
     * @return 时间间隔，默认5秒
     */
    int interval() default 5;
    
    /**
     * 键生成维度
     * 
     * @return 键生成维度，默认用户+方法
     */
    KeyDimension dimension() default KeyDimension.USER_METHOD;
    
    /**
     * 自定义键表达式（SpEL表达式）
     * 
     * <p>当dimension为CUSTOM时使用。支持访问方法参数和Spring上下文。
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
     * 重复提交时的提示消息
     * 
     * @return 提示消息，默认为通用提示
     */
    String message() default "请勿重复提交，请稍候再试";
    
    /**
     * 是否启用
     * 
     * <p>可以通过配置动态控制是否启用防重复提交功能。
     * 
     * @return 是否启用，默认true
     */
    boolean enabled() default true;
    
    /**
     * 键生成维度枚举
     */
    enum KeyDimension {
        /**
         * 用户+方法维度
         * 
         * <p>基于用户ID和方法签名生成键，确保不同用户之间不互相影响。
         * 适用于大多数用户操作场景。
         */
        USER_METHOD,
        
        /**
         * IP+方法维度
         * 
         * <p>基于客户端IP和方法签名生成键，适用于匿名用户或需要基于IP限制的场景。
         * 注意：相同IP的不同用户会互相影响。
         */
        IP_METHOD,
        
        /**
         * 会话+方法维度
         * 
         * <p>基于会话ID和方法签名生成键，适用于基于会话的防重复场景。
         */
        SESSION_METHOD,
        
        /**
         * 全局方法维度
         * 
         * <p>基于方法签名生成键，所有用户共享防重复状态。
         * 适用于全局资源操作或系统级操作。
         */
        GLOBAL_METHOD,
        
        /**
         * 自定义维度
         * 
         * <p>使用keyExpression指定的SpEL表达式计算键。
         * 提供最大的灵活性。
         */
        CUSTOM
    }
}
package com.twjgs.ratelimiter.controller;

import com.twjgs.ratelimiter.annotation.DuplicateSubmit;
import com.twjgs.ratelimiter.annotation.Idempotent;
import com.twjgs.ratelimiter.annotation.RateLimit;
import org.springframework.web.bind.annotation.*;

/**
 * API保护功能测试控制器
 * 
 * <p>用于测试防重复提交、幂等性控制、限流等功能
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
@RestController
@RequestMapping("/api/test")
public class ApiProtectionTestController {

    // ====== 防重复提交测试接口 ======
    
    /**
     * 基础防重复提交测试 - 5秒间隔
     */
    @PostMapping("/duplicate-submit/basic")
    @DuplicateSubmit
    public String basicDuplicateSubmit(@RequestBody(required = false) String content) {
        return "Basic duplicate submit test: " + (content != null ? content : "empty") + " at " + System.currentTimeMillis();
    }

    /**
     * 自定义间隔防重复提交测试 - 10秒间隔
     */
    @PostMapping("/duplicate-submit/custom-interval")
    @DuplicateSubmit(
        interval = 10,
        message = "操作过于频繁，请10秒后重试"
    )
    public String customIntervalDuplicateSubmit(@RequestBody(required = false) String data) {
        return "Custom interval duplicate submit test: " + System.currentTimeMillis();
    }

    /**
     * IP维度防重复提交测试 - 60秒间隔
     */
    @PostMapping("/duplicate-submit/ip-based")
    @DuplicateSubmit(
        interval = 60,
        dimension = DuplicateSubmit.KeyDimension.IP_METHOD,
        message = "该IP操作过于频繁，请60秒后重试"
    )
    public String ipBasedDuplicateSubmit(@RequestBody(required = false) String feedback) {
        return "IP-based duplicate submit test: " + System.currentTimeMillis();
    }

    /**
     * 全局方法维度防重复提交测试 - 30秒间隔
     */
    @PostMapping("/duplicate-submit/global")
    @DuplicateSubmit(
        interval = 30,
        dimension = DuplicateSubmit.KeyDimension.GLOBAL_METHOD,
        message = "系统繁忙，请30秒后重试"
    )
    public String globalDuplicateSubmit(@RequestBody(required = false) String config) {
        return "Global duplicate submit test: " + System.currentTimeMillis();
    }

    /**
     * 自定义键表达式防重复提交测试 - 300秒间隔
     */
    @PostMapping("/duplicate-submit/custom-key")
    @DuplicateSubmit(
        interval = 300,
        dimension = DuplicateSubmit.KeyDimension.CUSTOM,
        keyExpression = "'test:' + #request.getParameter('productId')",
        message = "请勿重复提交相同商品的测试请求"
    )
    public String customKeyDuplicateSubmit(@RequestBody(required = false) String orderData) {
        return "Custom key duplicate submit test: " + System.currentTimeMillis();
    }

    // ====== 幂等性控制测试接口 ======
    
    /**
     * 基础幂等性测试 - 300秒超时
     */
    @PostMapping("/idempotent/basic")
    @Idempotent(timeout = 300)
    public String basicIdempotent(@RequestBody(required = false) String data) {
        // 模拟处理时间
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Basic idempotent test - Resource created at " + System.currentTimeMillis();
    }

    /**
     * 参数哈希幂等性测试
     */
    @PostMapping("/idempotent/params-hash")
    @Idempotent(
        timeout = 600,
        keyStrategy = Idempotent.KeyStrategy.PARAMS_HASH,
        message = "请求正在处理中，请勿重复提交"
    )
    public String paramsHashIdempotent(@RequestParam(value = "orderId", required = true) String orderId, 
                                      @RequestParam(value = "userId", required = true) String userId,
                                      @RequestBody(required = false) String paymentData) {
        // 模拟支付处理
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Params hash idempotent test - Payment processed for order: " + orderId + ", user: " + userId + " at " + System.currentTimeMillis();
    }

    /**
     * 自定义键幂等性测试
     */
    @PostMapping("/idempotent/custom-key")
    @Idempotent(
        timeout = 600,
        keyStrategy = Idempotent.KeyStrategy.CUSTOM,
        keyExpression = "'payment:' + #request.getParameter('orderId')",
        message = "订单正在处理中，请勿重复提交"
    )
    public String customKeyIdempotent(@RequestBody(required = false) String paymentData) {
        // 模拟支付处理
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Custom key idempotent test - Payment processed at " + System.currentTimeMillis();
    }

    // ====== 限流控制测试接口 ======
    
    /**
     * 基础限流测试 - 每分钟10次
     */
    @GetMapping("/rate-limit/basic")
    @RateLimit(permits = 10, window = 60)
    public String basicRateLimit() {
        return "Basic rate limit test - 10 requests per minute: " + System.currentTimeMillis();
    }

    /**
     * IP限流测试 - 每30秒5次
     */
    @GetMapping("/rate-limit/ip-based")
    @RateLimit(
        dimension = RateLimit.LimitDimension.IP,
        permits = 5,
        window = 30,
        message = "您的IP访问过于频繁"
    )
    public String ipBasedRateLimit() {
        return "IP-based rate limit test - 5 requests per 30 seconds per IP: " + System.currentTimeMillis();
    }

    /**
     * 用户限流测试 - 令牌桶算法
     */
    @GetMapping("/rate-limit/user-based")
    @RateLimit(
        dimension = RateLimit.LimitDimension.USER,
        permits = 3,
        window = 60,
        algorithm = RateLimit.LimitAlgorithm.TOKEN_BUCKET,
        bucketCapacity = 5,
        refillRate = 0.1
    )
    public String userBasedRateLimit() {
        return "User-based rate limit test with token bucket: " + System.currentTimeMillis();
    }

    // ====== 组合功能测试接口 ======
    
    /**
     * 组合测试：限流 + 防重复提交
     */
    @PostMapping("/combined/rate-limit-duplicate")
    @RateLimit(permits = 5, window = 60, message = "访问频率过高")
    @DuplicateSubmit(interval = 10, message = "请勿重复提交")
    public String rateLimitAndDuplicateSubmit(@RequestBody(required = false) String data) {
        return "Rate limit + Duplicate submit test: " + System.currentTimeMillis();
    }

    /**
     * 组合测试：限流 + 幂等性控制
     */
    @PostMapping("/combined/rate-limit-idempotent")
    @RateLimit(permits = 3, window = 60, message = "访问频率过高")
    @Idempotent(timeout = 300, message = "操作正在处理中")
    public String rateLimitAndIdempotent(@RequestBody(required = false) String data) {
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Rate limit + Idempotent test: " + System.currentTimeMillis();
    }

    /**
     * 组合测试：防重复提交 + 幂等性控制
     */
    @PostMapping("/combined/duplicate-idempotent")
    @DuplicateSubmit(interval = 5, message = "请勿重复提交")
    @Idempotent(timeout = 300, message = "操作正在处理中")
    public String duplicateSubmitAndIdempotent(@RequestBody(required = false) String data) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Duplicate submit + Idempotent test: " + System.currentTimeMillis();
    }

    /**
     * 全功能组合测试：限流 + 防重复提交 + 幂等性控制
     */
    @PostMapping("/combined/all-features")
    @RateLimit(permits = 2, window = 60, message = "访问频率过高")
    @DuplicateSubmit(interval = 8, message = "请勿重复提交")
    @Idempotent(timeout = 300, message = "操作正在处理中")
    public String allFeaturesTest(@RequestBody(required = false) String data) {
        try {
            Thread.sleep(1200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "All features test - Rate limit + Duplicate submit + Idempotent: " + System.currentTimeMillis();
    }

    // ====== 状态查询接口 ======
    
    /**
     * 无保护的状态查询接口
     */
    @GetMapping("/status")
    public String getStatus() {
        return "API Protection Test Controller is running at " + System.currentTimeMillis();
    }
}
package io.github.rateLimiter.service;

import io.github.rateLimiter.model.DynamicRateLimitConfig;

/**
 * 文件日志服务接口 - 记录限流配置变更
 * 
 * @author RateLimiter Team
 * @since 1.0.0
 */
public interface FileLogService {
    
    /**
     * 记录配置变更到文件
     * 格式示例：
     * 2024-08-21 15:30:25 | com.example.UserController.getUserInfo | 无限流 => @RateLimit(permits=10, window=60) | admin
     * 2024-08-21 15:31:10 | com.example.OrderController.createOrder | @RateLimit(permits=5, window=30) => @RateLimit(permits=10, window=60) | admin
     * 2024-08-21 15:32:05 | com.example.PayController.pay | @RateLimit(permits=3, window=60) => 无限流 | admin
     * 
     * @param methodSignature 方法签名
     * @param beforeConfig 变更前配置（null表示无限流）
     * @param afterConfig 变更后配置（null表示删除限流）
     * @param operator 操作人
     */
    void logConfigChange(String methodSignature, 
                        DynamicRateLimitConfig beforeConfig, 
                        DynamicRateLimitConfig afterConfig, 
                        String operator);
}
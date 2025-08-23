package com.twjgs.ratelimiter.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 幂等性配置模型
 * 
 * @author RateLimiter Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotentConfig {
    
    /**
     * 方法签名
     */
    private String methodSignature;
    
    /**
     * 是否启用
     */
    private boolean enabled;
    
    /**
     * 超时时间（秒）
     */
    private int timeout;
    
    /**
     * 键生成策略
     */
    private String keyStrategy;
    
    /**
     * 自定义键表达式
     */
    private String keyExpression;
    
    /**
     * 是否返回第一次结果
     */
    private boolean returnFirstResult;
    
    /**
     * 提示消息
     */
    private String message;
    
    /**
     * 失败时是否允许重试
     */
    private boolean allowRetryOnFailure;
    
    /**
     * 失败检测策略
     */
    private String failureDetection;
    
    /**
     * 指定的失败异常类型（JSON数组格式）
     */
    private String failureExceptions;
    
    /**
     * 自定义失败检测条件（SpEL表达式）
     */
    private String failureCondition;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 操作人
     */
    private String operator;
}
package com.twjgs.ratelimiter.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 防重复提交配置模型
 * 
 * @author RateLimiter Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DuplicateSubmitConfig {
    
    /**
     * 方法签名
     */
    private String methodSignature;
    
    /**
     * 是否启用
     */
    private boolean enabled;
    
    /**
     * 间隔时间（秒）
     */
    private int interval;
    
    /**
     * 检查维度
     */
    private String dimension;
    
    /**
     * 自定义键表达式
     */
    private String keyExpression;
    
    /**
     * 提示消息
     */
    private String message;
    
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
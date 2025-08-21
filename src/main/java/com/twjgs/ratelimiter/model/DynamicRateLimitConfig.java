package com.twjgs.ratelimiter.model;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 动态限流配置
 * 
 * @author RateLimiter Team
 * @since 1.0.0
 */
@Data
public class DynamicRateLimitConfig {
    
    /**
     * 配置类型
     */
    private ConfigType type = ConfigType.SINGLE_RATE_LIMIT;
    
    /**
     * 是否启用
     */
    private Boolean enabled = true;
    
    /**
     * 限流维度
     */
    private String dimension;
    
    /**
     * 许可数量
     */
    private Integer permits;
    
    /**
     * 时间窗口（秒）
     */
    private Integer window;
    
    /**
     * 限流算法
     */
    private String algorithm = "SLIDING_WINDOW";
    
    /**
     * 令牌桶容量（仅TOKEN_BUCKET算法）
     */
    private Integer bucketCapacity;
    
    /**
     * 令牌补充速率（仅TOKEN_BUCKET算法）
     */
    private Double refillRate;
    
    /**
     * 限流策略
     */
    private String strategy = "REJECT";
    
    /**
     * 排队超时时间（毫秒）
     */
    private Long queueTimeout;
    
    /**
     * 错误消息
     */
    private String message;
    
    /**
     * 配置来源
     */
    private String source = "DYNAMIC";
    
    /**
     * 修改原因
     */
    private String reason;
    
    /**
     * 是否临时配置
     */
    private Boolean temporary = false;
    
    /**
     * 过期时间
     */
    private LocalDateTime expireTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime = LocalDateTime.now();
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime = LocalDateTime.now();
    
    /**
     * 操作人
     */
    private String operator;
    
    /**
     * 配置类型枚举
     */
    public enum ConfigType {
        /**
         * 单一限流配置
         */
        SINGLE_RATE_LIMIT,
        
        /**
         * 多重限流配置
         */
        MULTI_RATE_LIMIT,
        
        /**
         * 路径模式配置
         */
        PATH_PATTERN
    }
}
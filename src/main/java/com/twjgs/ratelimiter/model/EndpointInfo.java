package com.twjgs.ratelimiter.model;

import lombok.Data;
import com.twjgs.ratelimiter.annotation.RateLimit;
import com.twjgs.ratelimiter.annotation.Idempotent;
import com.twjgs.ratelimiter.annotation.DuplicateSubmit;
import java.util.List;

/**
 * 接口信息模型
 * 
 * @author RateLimiter Team
 * @since 1.0.0
 */
@Data
public class EndpointInfo {
    
    /**
     * 方法签名
     */
    private String methodSignature;
    
    /**
     * 类名
     */
    private String className;
    
    /**
     * 方法名
     */
    private String methodName;
    
    /**
     * HTTP方法
     */
    private String httpMethod;
    
    /**
     * 请求路径
     */
    private String path;
    
    /**
     * 限流注解配置
     */
    private RateLimit rateLimitAnnotation;
    
    /**
     * 幂等性注解配置
     */
    private Idempotent idempotentAnnotation;
    
    /**
     * 防重复提交注解配置
     */
    private DuplicateSubmit duplicateSubmitAnnotation;
    
    /**
     * 是否有注解配置（任一类型）
     */
    private boolean hasAnnotation;
    
    /**
     * 是否有限流注解
     */
    private boolean hasRateLimitAnnotation;
    
    /**
     * 是否有幂等性注解
     */
    private boolean hasIdempotentAnnotation;
    
    /**
     * 是否有防重复提交注解
     */
    private boolean hasDuplicateSubmitAnnotation;
    
    /**
     * 动态配置
     */
    private DynamicRateLimitConfig dynamicConfig;
    
    /**
     * 是否有动态配置
     */
    private boolean hasDynamicConfig;
    
    /**
     * 有效配置类型
     */
    private String effectiveConfigType;
    
    /**
     * 有效配置
     */
    private Object effectiveConfig;
    
    /**
     * 描述信息
     */
    private String description;
    
    /**
     * 标签
     */
    private List<String> tags;
}
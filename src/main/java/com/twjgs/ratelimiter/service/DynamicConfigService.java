package com.twjgs.ratelimiter.service;

import com.twjgs.ratelimiter.model.DynamicRateLimitConfig;
import java.util.Map;

/**
 * 动态配置服务接口
 * 
 * @author RateLimiter Team
 * @since 1.0.0
 */
public interface DynamicConfigService {
    
    /**
     * 获取动态配置
     * 
     * @param methodSignature 方法签名
     * @return 动态配置，如果没有则返回null
     */
    DynamicRateLimitConfig getDynamicConfig(String methodSignature);
    
    /**
     * 保存动态配置
     * 
     * @param methodSignature 方法签名
     * @param config 配置对象
     * @param operator 操作人
     */
    void saveDynamicConfig(String methodSignature, DynamicRateLimitConfig config, String operator);
    
    /**
     * 删除动态配置
     * 
     * @param methodSignature 方法签名
     * @param operator 操作人
     */
    void deleteDynamicConfig(String methodSignature, String operator);
    
    /**
     * 获取所有动态配置
     * 
     * @return 配置映射
     */
    Map<String, DynamicRateLimitConfig> getAllDynamicConfigs();
    
    /**
     * 保存路径模式配置
     * 
     * @param pathPattern 路径模式
     * @param httpMethod HTTP方法
     * @param config 配置对象
     * @param operator 操作人
     */
    void savePathPatternConfig(String pathPattern, String httpMethod, DynamicRateLimitConfig config, String operator);
    
    /**
     * 检查配置是否存在
     * 
     * @param methodSignature 方法签名
     * @return 是否存在
     */
    boolean hasConfig(String methodSignature);
    
    /**
     * 清理过期配置
     */
    void cleanExpiredConfigs();
}
package com.twjgs.ratelimiter.service;

import com.twjgs.ratelimiter.model.IdempotentConfig;
import java.util.List;
import java.util.Map;

/**
 * 幂等性配置服务接口
 * 
 * @author RateLimiter Team
 * @since 1.0.0
 */
public interface IdempotentConfigService {
    
    /**
     * 获取所有幂等性配置
     * 
     * @return 配置列表
     */
    List<IdempotentConfig> getAllConfigs();
    
    /**
     * 获取指定方法的幂等性配置
     * 
     * @param methodSignature 方法签名
     * @return 配置对象，如果没有则返回null
     */
    IdempotentConfig getConfig(String methodSignature);
    
    /**
     * 保存幂等性配置
     * 
     * @param config 配置对象
     */
    void saveConfig(IdempotentConfig config);
    
    /**
     * 删除幂等性配置
     * 
     * @param methodSignature 方法签名
     * @param operator 操作人
     * @return 是否删除成功
     */
    boolean deleteConfig(String methodSignature, String operator);
    
    /**
     * 检查配置是否存在
     * 
     * @param methodSignature 方法签名
     * @return 是否存在
     */
    boolean hasConfig(String methodSignature);
    
    /**
     * 获取所有配置映射
     * 
     * @return 配置映射
     */
    Map<String, IdempotentConfig> getAllConfigsAsMap();
}
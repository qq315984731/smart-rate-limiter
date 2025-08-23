package com.twjgs.ratelimiter.service;

import com.twjgs.ratelimiter.model.DuplicateSubmitConfig;
import java.util.List;
import java.util.Map;

/**
 * 防重复提交配置服务接口
 * 
 * @author RateLimiter Team
 * @since 1.0.0
 */
public interface DuplicateSubmitConfigService {
    
    /**
     * 获取所有防重复提交配置
     * 
     * @return 配置列表
     */
    List<DuplicateSubmitConfig> getAllConfigs();
    
    /**
     * 获取指定方法的防重复提交配置
     * 
     * @param methodSignature 方法签名
     * @return 配置对象，如果没有则返回null
     */
    DuplicateSubmitConfig getConfig(String methodSignature);
    
    /**
     * 保存防重复提交配置
     * 
     * @param config 配置对象
     */
    void saveConfig(DuplicateSubmitConfig config);
    
    /**
     * 删除防重复提交配置
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
    Map<String, DuplicateSubmitConfig> getAllConfigsAsMap();
}
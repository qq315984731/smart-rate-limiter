package com.twjgs.ratelimiter.service.impl;

import com.twjgs.ratelimiter.model.IdempotentConfig;
import com.twjgs.ratelimiter.service.IdempotentConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版幂等性配置服务实现
 * 
 * @author RateLimiter Team
 * @since 1.0.0
 */
@Slf4j
@Service
public class MemoryIdempotentConfigServiceImpl implements IdempotentConfigService {
    
    private final Map<String, IdempotentConfig> configStore = new ConcurrentHashMap<>();
    
    @Override
    public List<IdempotentConfig> getAllConfigs() {
        return new ArrayList<>(configStore.values());
    }
    
    @Override
    public IdempotentConfig getConfig(String methodSignature) {
        return configStore.get(methodSignature);
    }
    
    @Override
    public void saveConfig(IdempotentConfig config) {
        if (config == null || config.getMethodSignature() == null) {
            throw new IllegalArgumentException("配置或方法签名不能为空");
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        // 如果是新配置，设置创建时间
        if (config.getCreateTime() == null) {
            config.setCreateTime(now);
        }
        config.setUpdateTime(now);
        
        configStore.put(config.getMethodSignature(), config);
    }
    
    @Override
    public boolean deleteConfig(String methodSignature, String operator) {
        IdempotentConfig removed = configStore.remove(methodSignature);
        if (removed != null) {
            return true;
        }
        return false;
    }
    
    @Override
    public boolean hasConfig(String methodSignature) {
        return configStore.containsKey(methodSignature);
    }
    
    @Override
    public Map<String, IdempotentConfig> getAllConfigsAsMap() {
        return new HashMap<>(configStore);
    }
}
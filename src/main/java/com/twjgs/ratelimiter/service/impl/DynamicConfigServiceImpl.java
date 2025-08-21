package com.twjgs.ratelimiter.service.impl;

import com.twjgs.ratelimiter.model.DynamicRateLimitConfig;
import com.twjgs.ratelimiter.service.DynamicConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态配置服务实现
 * 
 * @author RateLimiter Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicConfigServiceImpl implements DynamicConfigService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String CONFIG_PREFIX = "rate_limit:dynamic_config:";
    private static final String PATH_PATTERN_PREFIX = "rate_limit:path_pattern:";
    
    // 本地缓存，提升性能
    private final Map<String, DynamicRateLimitConfig> localCache = new ConcurrentHashMap<>();
    
    @Override
    public DynamicRateLimitConfig getDynamicConfig(String methodSignature) {
        // 先从本地缓存获取
        DynamicRateLimitConfig cached = localCache.get(methodSignature);
        if (cached != null) {
            // 检查是否过期
            if (cached.getTemporary() && cached.getExpireTime() != null 
                && cached.getExpireTime().isBefore(LocalDateTime.now())) {
                // 配置已过期，删除
                deleteDynamicConfig(methodSignature, "SYSTEM");
                return null;
            }
            return cached;
        }
        
        // 从Redis获取
        try {
            String key = CONFIG_PREFIX + methodSignature;
            DynamicRateLimitConfig config = (DynamicRateLimitConfig) redisTemplate.opsForValue().get(key);
            
            if (config != null) {
                // 检查是否过期
                if (config.getTemporary() && config.getExpireTime() != null 
                    && config.getExpireTime().isBefore(LocalDateTime.now())) {
                    // 配置已过期，删除
                    deleteDynamicConfig(methodSignature, "SYSTEM");
                    return null;
                }
                
                // 放入本地缓存
                localCache.put(methodSignature, config);
                return config;
            }
        } catch (Exception e) {
            log.warn("Failed to get dynamic config from Redis for {}: {}", methodSignature, e.getMessage());
        }
        
        return null;
    }
    
    @Override
    public void saveDynamicConfig(String methodSignature, DynamicRateLimitConfig config, String operator) {
        try {
            // 获取原配置用于日志
            DynamicRateLimitConfig oldConfig = getDynamicConfig(methodSignature);
            
            // 设置操作信息
            config.setOperator(operator);
            config.setUpdateTime(LocalDateTime.now());
            if (oldConfig == null) {
                config.setCreateTime(LocalDateTime.now());
            } else {
                config.setCreateTime(oldConfig.getCreateTime());
            }
            
            // 保存到Redis
            String key = CONFIG_PREFIX + methodSignature;
            redisTemplate.opsForValue().set(key, config);
            
            // 更新本地缓存
            localCache.put(methodSignature, config);
            
            // 记录操作日志（简化）
            log.info("Config operation: method={}, operator={}, action={}", 
                methodSignature, operator, oldConfig == null ? "CREATE" : "UPDATE");
            
            log.info("Dynamic config saved: method={}, operator={}", methodSignature, operator);
            
        } catch (Exception e) {
            log.error("Failed to save dynamic config for {}: {}", methodSignature, e.getMessage(), e);
            throw new RuntimeException("保存配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteDynamicConfig(String methodSignature, String operator) {
        try {
            // 获取原配置用于日志
            DynamicRateLimitConfig oldConfig = getDynamicConfig(methodSignature);
            
            if (oldConfig != null) {
                // 从Redis删除
                String key = CONFIG_PREFIX + methodSignature;
                redisTemplate.delete(key);
                
                // 从本地缓存删除
                localCache.remove(methodSignature);
                
                // 记录操作日志（简化）
                log.info("Config operation: method={}, operator={}, action=DELETE", methodSignature, operator);
                
                log.info("Dynamic config deleted: method={}, operator={}", methodSignature, operator);
            }
            
        } catch (Exception e) {
            log.error("Failed to delete dynamic config for {}: {}", methodSignature, e.getMessage(), e);
            throw new RuntimeException("删除配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Map<String, DynamicRateLimitConfig> getAllDynamicConfigs() {
        Map<String, DynamicRateLimitConfig> allConfigs = new HashMap<>();
        
        try {
            Set<String> keys = redisTemplate.keys(CONFIG_PREFIX + "*");
            if (keys != null) {
                for (String key : keys) {
                    String methodSignature = key.substring(CONFIG_PREFIX.length());
                    DynamicRateLimitConfig config = getDynamicConfig(methodSignature);
                    if (config != null) {
                        allConfigs.put(methodSignature, config);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get all dynamic configs: {}", e.getMessage());
        }
        
        return allConfigs;
    }
    
    @Override
    public void savePathPatternConfig(String pathPattern, String httpMethod, 
                                    DynamicRateLimitConfig config, String operator) {
        try {
            String key = PATH_PATTERN_PREFIX + httpMethod + ":" + pathPattern;
            
            config.setOperator(operator);
            config.setUpdateTime(LocalDateTime.now());
            config.setCreateTime(LocalDateTime.now());
            
            redisTemplate.opsForValue().set(key, config);
            
            log.info("Path pattern config saved: pattern={}, method={}, operator={}", 
                    pathPattern, httpMethod, operator);
            
        } catch (Exception e) {
            log.error("Failed to save path pattern config: {}", e.getMessage(), e);
            throw new RuntimeException("保存路径模式配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean hasConfig(String methodSignature) {
        return getDynamicConfig(methodSignature) != null;
    }
    
    @Override
    public void cleanExpiredConfigs() {
        try {
            Set<String> keys = redisTemplate.keys(CONFIG_PREFIX + "*");
            if (keys != null) {
                int cleanedCount = 0;
                for (String key : keys) {
                    DynamicRateLimitConfig config = (DynamicRateLimitConfig) redisTemplate.opsForValue().get(key);
                    if (config != null && config.getTemporary() && config.getExpireTime() != null
                        && config.getExpireTime().isBefore(LocalDateTime.now())) {
                        
                        String methodSignature = key.substring(CONFIG_PREFIX.length());
                        deleteDynamicConfig(methodSignature, "SYSTEM");
                        cleanedCount++;
                    }
                }
                
                if (cleanedCount > 0) {
                    log.info("Cleaned {} expired dynamic configs", cleanedCount);
                }
            }
        } catch (Exception e) {
            log.error("Failed to clean expired configs: {}", e.getMessage(), e);
        }
    }
}
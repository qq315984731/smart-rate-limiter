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
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 动态配置服务实现
 * 
 * @author RateLimiter Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class DynamicConfigServiceImpl implements DynamicConfigService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String CONFIG_PREFIX = "rate_limit:dynamic_config:";
    private static final String PATH_PATTERN_PREFIX = "rate_limit:path_pattern:";
    
    // 本地缓存，提升性能
    private final Map<String, DynamicRateLimitConfig> localCache = new ConcurrentHashMap<>();
    
    // 读写锁，保证缓存操作的线程安全性
    private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();
    
    @Override
    public DynamicRateLimitConfig getDynamicConfig(String methodSignature) {
        // 使用读锁获取缓存
        cacheLock.readLock().lock();
        try {
            DynamicRateLimitConfig cached = localCache.get(methodSignature);
            if (cached != null) {
                // 检查是否过期
                if (cached.getTemporary() && cached.getExpireTime() != null 
                    && cached.getExpireTime().isBefore(LocalDateTime.now())) {
                    // 配置已过期，需要删除，升级为写锁
                    cacheLock.readLock().unlock();
                    cacheLock.writeLock().lock();
                    try {
                        // 再次检查（双重检查锁定模式）
                        cached = localCache.get(methodSignature);
                        if (cached != null && cached.getTemporary() && cached.getExpireTime() != null 
                            && cached.getExpireTime().isBefore(LocalDateTime.now())) {
                            localCache.remove(methodSignature);
                            // 从 Redis 也删除
                            String key = CONFIG_PREFIX + methodSignature;
                            redisTemplate.delete(key);
                        }
                        return null;
                    } finally {
                        cacheLock.readLock().lock();
                        cacheLock.writeLock().unlock();
                    }
                }
                return cached;
            }
        } finally {
            cacheLock.readLock().unlock();
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
                
                // 放入本地缓存（使用写锁）
                cacheLock.writeLock().lock();
                try {
                    localCache.put(methodSignature, config);
                } finally {
                    cacheLock.writeLock().unlock();
                }
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
            
            // 更新本地缓存（使用写锁）
            cacheLock.writeLock().lock();
            try {
                localCache.put(methodSignature, config);
            } finally {
                cacheLock.writeLock().unlock();
            }
            
            // 记录操作日志（简化）
            
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
                
                // 从本地缓存删除（使用写锁）
                cacheLock.writeLock().lock();
                try {
                    localCache.remove(methodSignature);
                } finally {
                    cacheLock.writeLock().unlock();
                }
                
                // 记录操作日志（简化）
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
                
            }
        } catch (Exception e) {
            log.error("Failed to clean expired configs: {}", e.getMessage(), e);
        }
    }
}
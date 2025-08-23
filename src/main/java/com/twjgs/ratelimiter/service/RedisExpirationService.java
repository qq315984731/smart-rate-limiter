package com.twjgs.ratelimiter.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis过期策略服务
 * 
 * <p>负责为幂等性和防重复提交的缓存数据设置合适的过期时间，
 * 确保数据在超时后自动清理，避免内存泄漏。
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
@Service
@Slf4j
@ConditionalOnBean(RedisTemplate.class)
@ConditionalOnProperty(prefix = "smart.api-protection", name = "storage.type", havingValue = "redis")
public class RedisExpirationService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisExpirationService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 为键设置过期时间
     * 
     * @param key Redis键
     * @param timeoutSeconds 超时时间（秒）
     */
    @Async
    public void setExpiration(String key, int timeoutSeconds) {
        try {
            // 设置稍微长一点的TTL，给处理留出缓冲时间
            long ttlSeconds = timeoutSeconds + 10; // 额外10秒缓冲
            
            Boolean result = redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
            
            if (Boolean.TRUE.equals(result)) {
                log.debug("Set expiration for key: {} to {} seconds", key, ttlSeconds);
            } else {
                log.warn("Failed to set expiration for key: {}", key);
            }
            
        } catch (Exception e) {
            log.error("Error setting expiration for key: {}", key, e);
        }
    }

    /**
     * 立即删除过期的键
     * 
     * @param key Redis键
     */
    @Async
    public void expireKeyNow(String key) {
        try {
            Boolean result = redisTemplate.delete(key);
            
            if (Boolean.TRUE.equals(result)) {
                log.debug("Immediately expired key: {}", key);
            } else {
                log.debug("Key not found or already expired: {}", key);
            }
            
        } catch (Exception e) {
            log.error("Error expiring key: {}", key, e);
        }
    }

    /**
     * 定期清理可能遗留的过期数据
     * 每小时执行一次，清理超时但未被自动删除的键
     */
    @Scheduled(fixedRate = 3600000) // 1小时
    public void cleanupExpiredData() {
        log.debug("Starting scheduled cleanup of expired data");
        
        try {
            // 查找所有API保护相关的键
            Set<String> keys = redisTemplate.keys("*smart:*");
            
            if (keys == null || keys.isEmpty()) {
                return;
            }
            
            int cleanedCount = 0;
            
            for (String key : keys) {
                try {
                    // 检查TTL，如果已过期但仍存在，则删除
                    Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                    
                    if (ttl != null && ttl == -1) {
                        // TTL为-1表示键没有设置过期时间，这可能是异常情况
                        // 对于API保护数据，我们应该设置过期时间
                        log.warn("Found key without TTL: {}, setting default expiration", key);
                        redisTemplate.expire(key, 24, TimeUnit.HOURS); // 设置24小时默认过期
                    }
                    
                } catch (Exception e) {
                    log.debug("Error checking TTL for key: {}", key, e);
                }
            }
            
            if (cleanedCount > 0) {
                log.info("Cleaned up {} expired keys during scheduled cleanup", cleanedCount);
            }
            
        } catch (Exception e) {
            log.error("Error during scheduled cleanup", e);
        }
    }

    /**
     * 批量设置多个键的过期时间
     * 
     * @param keys 键集合
     * @param timeoutSeconds 超时时间（秒）
     */
    @Async
    public void setBatchExpiration(Set<String> keys, int timeoutSeconds) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        
        long ttlSeconds = timeoutSeconds + 60; // 额外60秒缓冲
        
        for (String key : keys) {
            try {
                redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.debug("Error setting expiration for key: {}", key, e);
            }
        }
        
        log.debug("Set expiration for {} keys to {} seconds", keys.size(), ttlSeconds);
    }

    /**
     * 获取键的剩余TTL
     * 
     * @param key Redis键
     * @return 剩余TTL（秒），-1表示永不过期，-2表示键不存在
     */
    public long getKeyTTL(String key) {
        try {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return ttl != null ? ttl : -2;
        } catch (Exception e) {
            log.debug("Error getting TTL for key: {}", key, e);
            return -2;
        }
    }
}
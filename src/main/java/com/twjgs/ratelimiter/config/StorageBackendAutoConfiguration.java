package com.twjgs.ratelimiter.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 存储后端自动配置基础类
 * 统一管理Redis和Memory存储的条件判断逻辑
 * 
 * @author Smart Rate Limiter Team  
 * @since 1.1.0
 */
public class StorageBackendAutoConfiguration {
    
    /**
     * Redis存储后端条件判断
     */
    public static class RedisStorageCondition {
        
        /**
         * 检查是否应该启用Redis存储
         * 同时支持两种配置前缀以保持向后兼容性
         */
        @ConditionalOnClass({StringRedisTemplate.class, RedisTemplate.class})
        @ConditionalOnProperty(
            prefix = "smart", 
            name = {"rate-limiter.storage-type", "api-protection.storage.type"}, 
            havingValue = "redis", 
            matchIfMissing = false
        )
        public static class Redis {}
        
        /**
         * 仅基于rate-limiter配置的Redis存储
         */
        @ConditionalOnClass({StringRedisTemplate.class, RedisTemplate.class})
        @ConditionalOnProperty(
            prefix = "smart.rate-limiter", 
            name = "storage-type", 
            havingValue = "redis", 
            matchIfMissing = false
        )
        public static class RateLimiterRedis {}
        
        /**
         * 仅基于api-protection配置的Redis存储
         */
        @ConditionalOnClass({StringRedisTemplate.class, RedisTemplate.class})
        @ConditionalOnProperty(
            prefix = "smart.api-protection", 
            name = "storage.type", 
            havingValue = "redis", 
            matchIfMissing = false
        )
        public static class ApiProtectionRedis {}
    }
    
    /**
     * Memory存储后端条件判断
     */
    public static class MemoryStorageCondition {
        
        /**
         * 检查是否应该启用Memory存储
         * 当没有明确指定Redis时，默认使用Memory
         */
        @ConditionalOnProperty(
            prefix = "smart", 
            name = {"rate-limiter.storage-type", "api-protection.storage.type"}, 
            havingValue = "memory", 
            matchIfMissing = true
        )
        public static class Memory {}
        
        /**
         * 仅基于rate-limiter配置的Memory存储
         */
        @ConditionalOnProperty(
            prefix = "smart.rate-limiter", 
            name = "storage-type", 
            havingValue = "memory", 
            matchIfMissing = true
        )
        public static class RateLimiterMemory {}
        
        /**
         * 仅基于api-protection配置的Memory存储
         */
        @ConditionalOnProperty(
            prefix = "smart.api-protection", 
            name = "storage.type", 
            havingValue = "memory", 
            matchIfMissing = true
        )
        public static class ApiProtectionMemory {}
    }
    
    /**
     * 存储类型枚举
     */
    public enum StorageType {
        REDIS("redis"),
        MEMORY("memory");
        
        private final String value;
        
        StorageType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static StorageType fromValue(String value) {
            for (StorageType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return MEMORY; // 默认返回Memory
        }
    }
    
    /**
     * 检查当前配置的存储类型
     */
    public static StorageType getCurrentStorageType(ApiProtectionProperties properties) {
        if (properties != null && properties.getStorage() != null) {
            String type = properties.getStorage().getType();
            return StorageType.fromValue(type);
        }
        return StorageType.MEMORY;
    }
}
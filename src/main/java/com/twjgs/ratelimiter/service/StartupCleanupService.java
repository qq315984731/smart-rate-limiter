package com.twjgs.ratelimiter.service;

import com.twjgs.ratelimiter.config.ApiProtectionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Set;

/**
 * 启动清理服务
 * 
 * <p>在服务启动时清理幂等性和防重复提交的相关数据，确保每次重启都是全新状态。
 * 这对于分布式微服务环境特别重要，多个实例重启时都应该从干净状态开始。
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
@Service
@Slf4j
@ConditionalOnClass(RedisTemplate.class)
@ConditionalOnBean(RedisTemplate.class)
@ConditionalOnProperty(prefix = "smart.api-protection", name = "storage.type", havingValue = "redis", matchIfMissing = false)
public class StartupCleanupService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ApiProtectionProperties apiProtectionProperties;
    
    // 不需要清理的键模式（限流等应该保留的数据）
    private static final String[] PRESERVE_PATTERNS = {
        "rate_limit:*",        // 限流数据
        "*:rate_limit:*",      // 带前缀的限流数据
        "rate_limiter:*"       // 限流器数据
    };

    public StartupCleanupService(RedisTemplate<String, Object> redisTemplate, 
                               ApiProtectionProperties apiProtectionProperties) {
        this.redisTemplate = redisTemplate;
        this.apiProtectionProperties = apiProtectionProperties;
    }

    @PostConstruct
    public void cleanupOnStartup() {
        log.info("Starting cleanup on service startup");
        
        long totalCleaned = 0;
        
        try {
            // 清理API保护数据（幂等性、防重复提交）
            if (apiProtectionProperties.isStartupCleanupEnabled()) {
                totalCleaned += cleanupApiProtectionData();
            }
            
            // 清理动态配置数据
            if (apiProtectionProperties.isStartupCleanupDynamicConfig()) {
                totalCleaned += cleanupDynamicConfigData();
            }
            
            log.info("Startup cleanup completed. Total cleaned keys: {}", totalCleaned);
            
        } catch (Exception e) {
            log.error("Failed to perform startup cleanup", e);
            // 不抛异常，避免影响应用启动
        }
    }
    
    /**
     * 清理API保护数据（幂等性、防重复提交）
     */
    private long cleanupApiProtectionData() {
        log.info("Cleaning API protection data (idempotent, duplicate submit)");
        
        long totalCleaned = 0;
        
        // 使用配置的键前缀构建清理模式
        String[] cleanupPatterns = {
            // 幂等性相关键模式
            "*" + apiProtectionProperties.getKeyPrefix().getFullIdempotentPrefix() + "*",
            
            // 防重复提交相关键模式
            "*" + apiProtectionProperties.getKeyPrefix().getFullDuplicateSubmitPrefix() + "*"
        };
        
        for (String pattern : cleanupPatterns) {
            long cleaned = cleanupByPattern(pattern);
            totalCleaned += cleaned;
            
            if (cleaned > 0) {
                log.info("Cleaned {} API protection keys with pattern: {}", cleaned, pattern);
            }
        }
        
        return totalCleaned;
    }
    
    /**
     * 清理动态配置数据
     */
    private long cleanupDynamicConfigData() {
        log.info("Cleaning dynamic configuration data");
        
        String configPattern = "*" + apiProtectionProperties.getKeyPrefix().getFullDynamicConfigPrefix() + "*";
        long cleaned = cleanupByPattern(configPattern);
        
        if (cleaned > 0) {
            log.info("Cleaned {} dynamic config keys with pattern: {}", cleaned, configPattern);
        }
        
        return cleaned;
    }
    
    /**
     * 按模式清理Redis键
     * 
     * @param pattern 键模式
     * @return 清理的键数量
     */
    private long cleanupByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys == null || keys.isEmpty()) {
                return 0;
            }
            
            // 过滤掉需要保留的键
            Set<String> filteredKeys = keys.stream()
                .filter(this::shouldCleanup)
                .collect(java.util.stream.Collectors.toSet());
            
            if (filteredKeys.isEmpty()) {
                return 0;
            }
            
            // 批量删除
            Long deletedCount = redisTemplate.delete(filteredKeys);
            return deletedCount != null ? deletedCount : 0;
            
        } catch (Exception e) {
            log.warn("Failed to cleanup keys with pattern '{}': {}", pattern, e.getMessage());
            return 0;
        }
    }
    
    /**
     * 判断键是否应该被清理
     * 
     * @param key Redis键
     * @return true如果应该清理，false如果应该保留
     */
    private boolean shouldCleanup(String key) {
        // 检查是否是需要保留的键
        for (String preservePattern : PRESERVE_PATTERNS) {
            if (matchesPattern(key, preservePattern)) {
                log.debug("Preserving key (matches preserve pattern '{}'): {}", preservePattern, key);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 简单的通配符模式匹配
     * 
     * @param text 要匹配的文本
     * @param pattern 包含*的模式
     * @return 是否匹配
     */
    private boolean matchesPattern(String text, String pattern) {
        if (pattern.equals("*")) {
            return true;
        }
        
        // 简单实现：只处理开头和结尾的*
        if (pattern.startsWith("*") && pattern.endsWith("*")) {
            String middle = pattern.substring(1, pattern.length() - 1);
            return text.contains(middle);
        } else if (pattern.startsWith("*")) {
            String suffix = pattern.substring(1);
            return text.endsWith(suffix);
        } else if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return text.startsWith(prefix);
        } else {
            return text.equals(pattern);
        }
    }
    
    /**
     * 手动触发清理（可用于管理接口）
     * 
     * @return 清理的键数量
     */
    public long manualCleanup() {
        log.info("Manual cleanup triggered");
        
        long totalCleaned = 0;
        
        // 清理API保护数据
        totalCleaned += cleanupApiProtectionData();
        
        // 清理动态配置数据
        totalCleaned += cleanupDynamicConfigData();
        
        log.info("Manual cleanup completed. Total cleaned keys: {}", totalCleaned);
        return totalCleaned;
    }
}
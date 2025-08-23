package com.twjgs.ratelimiter.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twjgs.ratelimiter.config.RateLimiterProperties;
import com.twjgs.ratelimiter.config.ApiProtectionProperties;
import com.twjgs.ratelimiter.model.IdempotentRecord;
import com.twjgs.ratelimiter.service.IdempotentService;
import com.twjgs.ratelimiter.service.RedisExpirationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Redis实现的幂等性服务
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
@Slf4j
public class RedisIdempotentService implements IdempotentService {

    private static final String EXECUTING_STATUS = "EXECUTING";
    private static final String SUCCESS_STATUS = "SUCCESS";
    private static final String FAILED_STATUS = "FAILED";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RateLimiterProperties properties;
    private final ApiProtectionProperties apiProtectionProperties;
    private final RedisExpirationService redisExpirationService;
    
    // Lua脚本：原子性检查并创建幂等记录
    private final DefaultRedisScript<String> checkAndCreateScript;
    
    // Lua脚本：原子性更新执行结果
    private final DefaultRedisScript<Boolean> updateResultScript;

    public RedisIdempotentService(RedisTemplate<String, Object> redisTemplate, 
                                  ObjectMapper objectMapper,
                                  RateLimiterProperties properties,
                                  ApiProtectionProperties apiProtectionProperties,
                                  RedisExpirationService redisExpirationService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.apiProtectionProperties = apiProtectionProperties;
        this.redisExpirationService = redisExpirationService;
        
        // 初始化Lua脚本
        this.checkAndCreateScript = new DefaultRedisScript<>();
        this.checkAndCreateScript.setScriptSource(new ResourceScriptSource(
            new ClassPathResource("lua/idempotent_check_and_create.lua")));
        this.checkAndCreateScript.setResultType(String.class);
        
        this.updateResultScript = new DefaultRedisScript<>();
        this.updateResultScript.setScriptSource(new ResourceScriptSource(
            new ClassPathResource("lua/idempotent_update_result.lua")));
        this.updateResultScript.setResultType(Boolean.class);
    }

    @Override
    public IdempotentRecord checkIdempotent(String key, int timeoutSeconds) {
        String redisKey = buildRedisKey(key);
        
        try {
            // 尝试获取现有记录
            Object recordObj = redisTemplate.opsForValue().get(redisKey);
            if (recordObj != null) {
                IdempotentRecord record = deserializeRecord(recordObj.toString());
                if (record != null && !record.isExpired()) {
                    record.updateLastAccessTime();
                    return record;
                }
            }
            
            return null;
        } catch (Exception e) {
            log.error("Failed to check idempotent for key: {}", key, e);
            return null;
        }
    }

    @Override
    public IdempotentRecord createExecutingRecord(String key, String methodSignature, 
                                                  String parametersHash, String userId, 
                                                  int timeoutSeconds) {
        String redisKey = buildRedisKey(key);
        
        try {
            // 使用Lua脚本原子性检查并创建记录
            String currentTime = LocalDateTime.now().toString();
            String expireTime = LocalDateTime.now().plusSeconds(timeoutSeconds).toString();
            
            String result = redisTemplate.execute(checkAndCreateScript, 
                Collections.singletonList(redisKey),
                EXECUTING_STATUS, methodSignature, parametersHash, 
                userId != null ? userId : "", currentTime, expireTime, 
                String.valueOf(timeoutSeconds));
            
            if ("CREATED".equals(result)) {
                // 记录创建成功，构建并返回记录对象
                IdempotentRecord record = IdempotentRecord.builder()
                    .key(key)
                    .methodSignature(methodSignature)
                    .parametersHash(parametersHash)
                    .userId(userId)
                    .status(IdempotentRecord.ExecutionStatus.EXECUTING)
                    .firstRequestTime(LocalDateTime.now())
                    .expireTime(LocalDateTime.now().plusSeconds(timeoutSeconds))
                    .createTime(LocalDateTime.now())
                    .lastAccessTime(LocalDateTime.now())
                    .accessCount(1)
                    .build();
                
                // 设置Redis键的自动过期时间
                if (redisExpirationService != null) {
                    redisExpirationService.setExpiration(redisKey, timeoutSeconds);
                }
                
                return record;
            } else if ("EXISTS".equals(result)) {
                // 记录已存在，返回现有记录
                return getRecord(key);
            }
            
            return null;
        } catch (Exception e) {
            log.error("Failed to create executing record for key: {}", key, e);
            return null;
        }
    }

    @Override
    public void markSuccess(String key, String result) {
        String redisKey = buildRedisKey(key);
        
        try {
            // 使用Lua脚本原子性更新结果
            redisTemplate.execute(updateResultScript,
                Collections.singletonList(redisKey),
                SUCCESS_STATUS, result, LocalDateTime.now().toString());
            
        } catch (Exception e) {
            log.error("Failed to mark success for key: {}", key, e);
        }
    }

    @Override
    public void markFailed(String key, String errorMessage) {
        String redisKey = buildRedisKey(key);
        
        try {
            // 使用Lua脚本原子性更新结果
            redisTemplate.execute(updateResultScript,
                Collections.singletonList(redisKey),
                FAILED_STATUS, errorMessage, LocalDateTime.now().toString());
            
        } catch (Exception e) {
            log.error("Failed to mark failed for key: {}", key, e);
        }
    }

    @Override
    public IdempotentRecord getRecord(String key) {
        String redisKey = buildRedisKey(key);
        
        try {
            Object recordObj = redisTemplate.opsForValue().get(redisKey);
            if (recordObj != null) {
                return deserializeRecord(recordObj.toString());
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to get record for key: {}", key, e);
            return null;
        }
    }

    @Override
    public boolean deleteRecord(String key) {
        String redisKey = buildRedisKey(key);
        
        try {
            Boolean result = redisTemplate.delete(redisKey);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Failed to delete record for key: {}", key, e);
            return false;
        }
    }

    @Override
    public boolean existsRecord(String key) {
        String redisKey = buildRedisKey(key);
        
        try {
            Boolean result = redisTemplate.hasKey(redisKey);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Failed to check existence for key: {}", key, e);
            return false;
        }
    }

    @Override
    public long cleanExpiredRecords() {
        // Redis的TTL机制会自动清理过期记录
        // 这里返回0，表示由Redis自动处理
        return 0;
    }

    @Override
    public long getRecordCount() {
        try {
            String pattern = buildRedisKey("*");
            return redisTemplate.keys(pattern).size();
        } catch (Exception e) {
            log.error("Failed to get record count", e);
            return 0;
        }
    }

    /**
     * 构建Redis键
     * 
     * @param key 幂等键
     * @return Redis键
     */
    private String buildRedisKey(String key) {
        // 使用配置的幂等性键前缀
        String idempotentPrefix = apiProtectionProperties.getKeyPrefix().getFullIdempotentPrefix();
        
        // 兼容原有的全局键前缀配置
        String keyPrefix = properties.getRedis().getKeyPrefix();
        if (keyPrefix != null && !keyPrefix.isEmpty()) {
            if (keyPrefix.endsWith(":")) {
                return keyPrefix + idempotentPrefix + key;
            } else {
                return keyPrefix + ":" + idempotentPrefix + key;
            }
        } else {
            return idempotentPrefix + key;
        }
    }

    /**
     * 反序列化记录
     * 
     * @param json JSON字符串
     * @return 幂等记录
     */
    private IdempotentRecord deserializeRecord(String json) {
        try {
            return objectMapper.readValue(json, IdempotentRecord.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize idempotent record: {}", json, e);
            return null;
        }
    }

    /**
     * 序列化记录
     * 
     * @param record 幂等记录
     * @return JSON字符串
     */
    private String serializeRecord(IdempotentRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize idempotent record: {}", record, e);
            return null;
        }
    }
}
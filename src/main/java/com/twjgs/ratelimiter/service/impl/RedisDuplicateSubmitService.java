package com.twjgs.ratelimiter.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twjgs.ratelimiter.config.ApiProtectionProperties;
import com.twjgs.ratelimiter.model.DuplicateSubmitRecord;
import com.twjgs.ratelimiter.service.DuplicateSubmitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis实现的防重复提交服务
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
@Slf4j
public class RedisDuplicateSubmitService implements DuplicateSubmitService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ApiProtectionProperties apiProtectionProperties;
    
    /**
     * 检查和创建重复提交记录的Lua脚本
     */
    private static final String CHECK_AND_CREATE_SCRIPT = 
        "local key = KEYS[1]\n" +
        "local recordJson = ARGV[1]\n" +
        "local expireSeconds = tonumber(ARGV[2])\n" +
        "\n" +
        "-- 检查记录是否存在\n" +
        "local existingRecord = redis.call('GET', key)\n" +
        "if existingRecord then\n" +
        "    -- 更新访问时间和次数\n" +
        "    local record = cjson.decode(existingRecord)\n" +
        "    record.lastAccessTime = ARGV[3]\n" +
        "    record.accessCount = (record.accessCount or 1) + 1\n" +
        "    \n" +
        "    -- 更新记录\n" +
        "    redis.call('SET', key, cjson.encode(record), 'EX', expireSeconds)\n" +
        "    return existingRecord\n" +
        "else\n" +
        "    -- 创建新记录\n" +
        "    redis.call('SET', key, recordJson, 'EX', expireSeconds)\n" +
        "    return nil\n" +
        "end";
    
    /**
     * 清理过期记录的Lua脚本
     */
    private static final String CLEANUP_EXPIRED_SCRIPT = 
        "local pattern = KEYS[1]\n" +
        "local keys = redis.call('KEYS', pattern)\n" +
        "local cleaned = 0\n" +
        "local now = tonumber(ARGV[1])\n" +
        "\n" +
        "for i = 1, #keys do\n" +
        "    local recordJson = redis.call('GET', keys[i])\n" +
        "    if recordJson then\n" +
        "        local record = cjson.decode(recordJson)\n" +
        "        if record.expireTime and record.expireTime < now then\n" +
        "            redis.call('DEL', keys[i])\n" +
        "            cleaned = cleaned + 1\n" +
        "        end\n" +
        "    end\n" +
        "end\n" +
        "\n" +
        "return cleaned";

    public RedisDuplicateSubmitService(RedisTemplate<String, String> redisTemplate, 
                                      ObjectMapper objectMapper,
                                      ApiProtectionProperties apiProtectionProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.apiProtectionProperties = apiProtectionProperties;
    }

    @Override
    public DuplicateSubmitRecord checkDuplicate(String key, String methodSignature,
                                               String userId, String clientIp, String sessionId,
                                               int intervalSeconds, String dimension,
                                               String requestUri, String httpMethod, String userAgent) {
        String redisKey = buildRedisKey(key);
        
        try {
            // 创建新记录
            DuplicateSubmitRecord newRecord = DuplicateSubmitRecord.create(
                key, methodSignature, userId, clientIp, sessionId,
                intervalSeconds, dimension, requestUri, httpMethod, userAgent
            );
            
            String recordJson = objectMapper.writeValueAsString(newRecord);
            String now = LocalDateTime.now().toString();
            
            // 执行Lua脚本检查和创建记录
            String existingRecordJson = redisTemplate.execute(
                RedisScript.of(CHECK_AND_CREATE_SCRIPT, String.class),
                Collections.singletonList(redisKey),
                recordJson, String.valueOf(intervalSeconds), now
            );
            
            // 如果存在记录，说明是重复提交
            if (existingRecordJson != null) {
                return objectMapper.readValue(existingRecordJson, DuplicateSubmitRecord.class);
            }
            
            return null; // 不是重复提交
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize/deserialize duplicate submit record", e);
            throw new RuntimeException("Failed to process duplicate submit record", e);
        }
    }

    @Override
    public DuplicateSubmitRecord createRecord(String key, String methodSignature,
                                             String userId, String clientIp, String sessionId,
                                             int intervalSeconds, String dimension,
                                             String requestUri, String httpMethod, String userAgent) {
        try {
            DuplicateSubmitRecord record = DuplicateSubmitRecord.create(
                key, methodSignature, userId, clientIp, sessionId,
                intervalSeconds, dimension, requestUri, httpMethod, userAgent
            );
            
            String redisKey = buildRedisKey(key);
            String recordJson = objectMapper.writeValueAsString(record);
            
            redisTemplate.opsForValue().set(redisKey, recordJson, intervalSeconds, TimeUnit.SECONDS);
            
            return record;
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize duplicate submit record", e);
            throw new RuntimeException("Failed to create duplicate submit record", e);
        }
    }

    @Override
    public DuplicateSubmitRecord getRecord(String key) {
        try {
            String redisKey = buildRedisKey(key);
            String recordJson = redisTemplate.opsForValue().get(redisKey);
            
            if (recordJson == null) {
                return null;
            }
            
            DuplicateSubmitRecord record = objectMapper.readValue(recordJson, DuplicateSubmitRecord.class);
            
            // 检查是否过期
            if (record.isExpired()) {
                redisTemplate.delete(redisKey);
                return null;
            }
            
            return record;
            
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize duplicate submit record", e);
            return null;
        }
    }

    @Override
    public boolean deleteRecord(String key) {
        String redisKey = buildRedisKey(key);
        return Boolean.TRUE.equals(redisTemplate.delete(redisKey));
    }

    @Override
    public boolean existsRecord(String key) {
        String redisKey = buildRedisKey(key);
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
    }

    @Override
    public long cleanExpiredRecords() {
        try {
            String pattern = apiProtectionProperties.getKeyPrefix().getFullDuplicateSubmitPrefix() + "*";
            long now = System.currentTimeMillis();
            
            Long cleaned = redisTemplate.execute(
                RedisScript.of(CLEANUP_EXPIRED_SCRIPT, Long.class),
                Collections.singletonList(pattern),
                String.valueOf(now)
            );
            
            if (cleaned != null && cleaned > 0) {
                log.debug("Cleaned {} expired duplicate submit records", cleaned);
            }
            
            return cleaned != null ? cleaned : 0;
            
        } catch (Exception e) {
            log.error("Failed to clean expired duplicate submit records", e);
            return 0;
        }
    }

    @Override
    public long getRecordCount() {
        try {
            String pattern = apiProtectionProperties.getKeyPrefix().getFullDuplicateSubmitPrefix() + "*";
            Set<String> keys = redisTemplate.keys(pattern);
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("Failed to get duplicate submit record count", e);
            return 0;
        }
    }

    @Override
    public long getRecordCountByDimension(String dimension) {
        try {
            String pattern = apiProtectionProperties.getKeyPrefix().getFullDuplicateSubmitPrefix() + "dimension:" + dimension + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("Failed to get duplicate submit record count by dimension", e);
            return 0;
        }
    }

    @Override
    public long getRecordCountByUser(String userId) {
        if (userId == null) {
            return 0;
        }
        
        try {
            String pattern = apiProtectionProperties.getKeyPrefix().getFullDuplicateSubmitPrefix() + "user:" + userId + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("Failed to get duplicate submit record count by user", e);
            return 0;
        }
    }

    /**
     * 构建Redis键
     */
    private String buildRedisKey(String key) {
        return apiProtectionProperties.getKeyPrefix().getFullDuplicateSubmitPrefix() + key;
    }
}
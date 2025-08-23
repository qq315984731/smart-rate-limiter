package com.twjgs.ratelimiter.service.impl;

import com.twjgs.ratelimiter.model.DuplicateSubmitRecord;
import com.twjgs.ratelimiter.service.DuplicateSubmitService;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 内存实现的防重复提交服务
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
@Slf4j
public class MemoryDuplicateSubmitService implements DuplicateSubmitService {

    private final ConcurrentHashMap<String, DuplicateSubmitRecord> recordMap;
    private final ConcurrentHashMap<String, ReentrantLock> lockMap;
    private final ScheduledExecutorService cleanupExecutor;

    public MemoryDuplicateSubmitService() {
        this.recordMap = new ConcurrentHashMap<>();
        this.lockMap = new ConcurrentHashMap<>();
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DuplicateSubmitCleanupThread");
            t.setDaemon(true);
            return t;
        });
        
        // 每2分钟清理一次过期记录
        this.cleanupExecutor.scheduleAtFixedRate(this::cleanExpiredRecords, 
            2, 2, TimeUnit.MINUTES);
    }

    @Override
    public DuplicateSubmitRecord checkDuplicate(String key, String methodSignature,
                                               String userId, String clientIp, String sessionId,
                                               int intervalSeconds, String dimension,
                                               String requestUri, String httpMethod, String userAgent) {
        
        // 获取或创建锁
        ReentrantLock lock = lockMap.computeIfAbsent(key, k -> new ReentrantLock());
        
        lock.lock();
        try {
            DuplicateSubmitRecord existingRecord = recordMap.get(key);
            
            if (existingRecord != null) {
                if (existingRecord.isExpired()) {
                    // 记录已过期，删除旧记录
                    recordMap.remove(key);
                    lockMap.remove(key);
                } else {
                    // 记录未过期，属于重复提交
                    existingRecord.updateLastAccess();
                    return existingRecord;
                }
            }
            
            // 创建新记录
            DuplicateSubmitRecord newRecord = DuplicateSubmitRecord.create(
                key, methodSignature, userId, clientIp, sessionId,
                intervalSeconds, dimension, requestUri, httpMethod, userAgent
            );
            
            recordMap.put(key, newRecord);
            return null; // 表示不是重复提交
            
        } finally {
            lock.unlock();
        }
    }

    @Override
    public DuplicateSubmitRecord createRecord(String key, String methodSignature,
                                             String userId, String clientIp, String sessionId,
                                             int intervalSeconds, String dimension,
                                             String requestUri, String httpMethod, String userAgent) {
        
        DuplicateSubmitRecord record = DuplicateSubmitRecord.create(
            key, methodSignature, userId, clientIp, sessionId,
            intervalSeconds, dimension, requestUri, httpMethod, userAgent
        );
        
        recordMap.put(key, record);
        return record;
    }

    @Override
    public DuplicateSubmitRecord getRecord(String key) {
        DuplicateSubmitRecord record = recordMap.get(key);
        if (record != null && record.isExpired()) {
            recordMap.remove(key);
            lockMap.remove(key);
            return null;
        }
        return record;
    }

    @Override
    public boolean deleteRecord(String key) {
        DuplicateSubmitRecord removed = recordMap.remove(key);
        lockMap.remove(key);
        return removed != null;
    }

    @Override
    public boolean existsRecord(String key) {
        DuplicateSubmitRecord record = recordMap.get(key);
        if (record != null && record.isExpired()) {
            recordMap.remove(key);
            lockMap.remove(key);
            return false;
        }
        return record != null;
    }

    @Override
    public long cleanExpiredRecords() {
        long cleaned = 0;
        LocalDateTime now = LocalDateTime.now();
        
        try {
            for (String key : recordMap.keySet()) {
                DuplicateSubmitRecord record = recordMap.get(key);
                if (record != null && record.getExpireTime() != null 
                    && now.isAfter(record.getExpireTime())) {
                    
                    recordMap.remove(key);
                    lockMap.remove(key);
                    cleaned++;
                }
            }
            
            if (cleaned > 0) {
                log.debug("Cleaned {} expired duplicate submit records", cleaned);
            }
        } catch (Exception e) {
            log.error("Failed to clean expired records", e);
        }
        
        return cleaned;
    }

    @Override
    public long getRecordCount() {
        return recordMap.size();
    }

    @Override
    public long getRecordCountByDimension(String dimension) {
        return recordMap.values().stream()
            .filter(record -> dimension.equals(record.getDimension()))
            .count();
    }

    @Override
    public long getRecordCountByUser(String userId) {
        if (userId == null) {
            return 0;
        }
        return recordMap.values().stream()
            .filter(record -> userId.equals(record.getUserId()))
            .count();
    }

    /**
     * 销毁服务，清理资源
     */
    public void destroy() {
        if (cleanupExecutor != null && !cleanupExecutor.isShutdown()) {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        recordMap.clear();
        lockMap.clear();
    }
}
package com.twjgs.ratelimiter.service.impl;

import com.twjgs.ratelimiter.model.IdempotentRecord;
import com.twjgs.ratelimiter.service.IdempotentService;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 内存实现的幂等性服务
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
@Slf4j
public class MemoryIdempotentService implements IdempotentService {

    private final ConcurrentHashMap<String, IdempotentRecord> recordMap;
    private final ConcurrentHashMap<String, ReentrantLock> lockMap;
    private final ScheduledExecutorService cleanupExecutor;

    public MemoryIdempotentService() {
        this.recordMap = new ConcurrentHashMap<>();
        this.lockMap = new ConcurrentHashMap<>();
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "IdempotentCleanupThread");
            t.setDaemon(true);
            return t;
        });
        
        // 每5分钟清理一次过期记录
        this.cleanupExecutor.scheduleAtFixedRate(this::cleanExpiredRecords, 
            5, 5, TimeUnit.MINUTES);
    }

    @Override
    public IdempotentRecord checkIdempotent(String key, int timeoutSeconds) {
        IdempotentRecord record = recordMap.get(key);
        if (record != null) {
            if (record.isExpired()) {
                // 记录已过期，删除
                recordMap.remove(key);
                lockMap.remove(key);
                return null;
            }
            
            record.updateLastAccessTime();
            return record;
        }
        
        return null;
    }

    @Override
    public IdempotentRecord createExecutingRecord(String key, String methodSignature, 
                                                  String parametersHash, String userId, 
                                                  int timeoutSeconds) {
        // 获取或创建锁
        ReentrantLock lock = lockMap.computeIfAbsent(key, k -> new ReentrantLock());
        
        lock.lock();
        try {
            // 双重检查，确保并发安全
            IdempotentRecord existingRecord = recordMap.get(key);
            if (existingRecord != null && !existingRecord.isExpired()) {
                existingRecord.updateLastAccessTime();
                return existingRecord;
            }
            
            // 创建新的执行中记录
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
            
            recordMap.put(key, record);
            return record;
            
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void markSuccess(String key, String result) {
        IdempotentRecord record = recordMap.get(key);
        if (record != null) {
            ReentrantLock lock = lockMap.get(key);
            if (lock != null) {
                lock.lock();
                try {
                    record.markAsSuccess(result);
                } finally {
                    lock.unlock();
                }
            } else {
                record.markAsSuccess(result);
            }
        }
    }

    @Override
    public void markFailed(String key, String errorMessage) {
        IdempotentRecord record = recordMap.get(key);
        if (record != null) {
            ReentrantLock lock = lockMap.get(key);
            if (lock != null) {
                lock.lock();
                try {
                    record.markAsFailed(errorMessage);
                } finally {
                    lock.unlock();
                }
            } else {
                record.markAsFailed(errorMessage);
            }
        }
    }

    @Override
    public IdempotentRecord getRecord(String key) {
        IdempotentRecord record = recordMap.get(key);
        if (record != null && record.isExpired()) {
            recordMap.remove(key);
            lockMap.remove(key);
            return null;
        }
        return record;
    }

    @Override
    public boolean deleteRecord(String key) {
        IdempotentRecord removed = recordMap.remove(key);
        lockMap.remove(key);
        return removed != null;
    }

    @Override
    public boolean existsRecord(String key) {
        IdempotentRecord record = recordMap.get(key);
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
                IdempotentRecord record = recordMap.get(key);
                if (record != null && record.getExpireTime() != null 
                    && now.isAfter(record.getExpireTime())) {
                    
                    recordMap.remove(key);
                    lockMap.remove(key);
                    cleaned++;
                }
            }
            
            if (cleaned > 0) {
                log.debug("Cleaned {} expired idempotent records", cleaned);
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
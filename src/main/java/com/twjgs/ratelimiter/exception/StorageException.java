package com.twjgs.ratelimiter.exception;

/**
 * 存储操作异常
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
public class StorageException extends ApiProtectionException {
    
    public static final String ERROR_CODE_REDIS_CONNECTION = "STORAGE_REDIS_CONNECTION_FAILED";
    public static final String ERROR_CODE_REDIS_OPERATION = "STORAGE_REDIS_OPERATION_FAILED";
    public static final String ERROR_CODE_MEMORY_OVERFLOW = "STORAGE_MEMORY_OVERFLOW";
    public static final String ERROR_CODE_SERIALIZATION = "STORAGE_SERIALIZATION_FAILED";
    
    public StorageException(String message, String errorCode) {
        super(message, errorCode);
    }
    
    public StorageException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
    
    @Override
    public String getErrorType() {
        return "STORAGE_ERROR";
    }
    
    @Override
    public int getHttpStatus() {
        return 503; // Service Unavailable
    }
    
    // 便捷的工厂方法
    public static StorageException redisConnectionFailed(String message, Throwable cause) {
        return new StorageException(message, ERROR_CODE_REDIS_CONNECTION, cause);
    }
    
    public static StorageException redisOperationFailed(String message, Throwable cause) {
        return new StorageException(message, ERROR_CODE_REDIS_OPERATION, cause);
    }
    
    public static StorageException memoryOverflow(String message) {
        return new StorageException(message, ERROR_CODE_MEMORY_OVERFLOW);
    }
    
    public static StorageException serializationFailed(String message, Throwable cause) {
        return new StorageException(message, ERROR_CODE_SERIALIZATION, cause);
    }
}
package com.twjgs.ratelimiter.exception;

/**
 * API保护通用异常基类
 * 统一所有API保护相关的异常处理
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
public abstract class ApiProtectionException extends RuntimeException {
    
    private final String errorCode;
    private final long timestamp;
    
    public ApiProtectionException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.timestamp = System.currentTimeMillis();
    }
    
    public ApiProtectionException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * 获取错误类型
     */
    public abstract String getErrorType();
    
    /**
     * 获取HTTP状态码
     */
    public abstract int getHttpStatus();
    
    /**
     * 是否需要记录错误日志
     */
    public boolean shouldLogError() {
        return true;
    }
}
package com.twjgs.ratelimiter.exception;

/**
 * 幂等性异常
 * 
 * <p>当检测到重复请求时抛出此异常。
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
public class IdempotentException extends ApiProtectionException {

    private static final long serialVersionUID = 1L;

    /**
     * 幂等键
     */
    private final String idempotentKey;

    /**
     * 第一次请求的时间戳
     */
    private final long firstRequestTime;

    /**
     * 构造幂等性异常
     * 
     * @param message 异常消息
     */
    public IdempotentException(String message) {
        super(message, "IDEMPOTENT_REQUEST_DUPLICATE");
        this.idempotentKey = null;
        this.firstRequestTime = 0;
    }

    /**
     * 构造幂等性异常
     * 
     * @param message 异常消息
     * @param idempotentKey 幂等键
     * @param firstRequestTime 第一次请求时间戳
     */
    public IdempotentException(String message, String idempotentKey, long firstRequestTime) {
        super(message, "IDEMPOTENT_REQUEST_DUPLICATE");
        this.idempotentKey = idempotentKey;
        this.firstRequestTime = firstRequestTime;
    }

    /**
     * 构造幂等性异常
     * 
     * @param message 异常消息
     * @param cause 原因异常
     */
    public IdempotentException(String message, Throwable cause) {
        super(message, "IDEMPOTENT_REQUEST_DUPLICATE", cause);
        this.idempotentKey = null;
        this.firstRequestTime = 0;
    }

    /**
     * 获取幂等键
     * 
     * @return 幂等键
     */
    public String getIdempotentKey() {
        return idempotentKey;
    }

    /**
     * 获取第一次请求时间戳
     * 
     * @return 第一次请求时间戳
     */
    public long getFirstRequestTime() {
        return firstRequestTime;
    }

    /**
     * 获取距离第一次请求的时间间隔（毫秒）
     * 
     * @return 时间间隔
     */
    public long getTimeSinceFirstRequest() {
        if (firstRequestTime <= 0) {
            return 0;
        }
        return System.currentTimeMillis() - firstRequestTime;
    }
    
    @Override
    public String getErrorType() {
        return "IDEMPOTENT";
    }
    
    @Override
    public int getHttpStatus() {
        return 409; // Conflict
    }
    
    @Override
    public boolean shouldLogError() {
        // 幂等性异常通常不需要记录错误日志，只需要警告
        return false;
    }
}
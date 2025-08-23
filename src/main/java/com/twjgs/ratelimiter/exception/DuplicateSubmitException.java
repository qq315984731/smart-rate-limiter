package com.twjgs.ratelimiter.exception;

/**
 * 防重复提交异常
 * 
 * <p>当检测到重复提交时抛出此异常。
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
public class DuplicateSubmitException extends ApiProtectionException {

    private static final long serialVersionUID = 1L;

    /**
     * 防重复键
     */
    private final String duplicateKey;

    /**
     * 首次提交的时间戳
     */
    private final long firstSubmitTime;

    /**
     * 防重复时间间隔（秒）
     */
    private final int intervalSeconds;

    /**
     * 构造防重复提交异常
     * 
     * @param message 异常消息
     */
    public DuplicateSubmitException(String message) {
        super(message, "DUPLICATE_SUBMIT_DETECTED");
        this.duplicateKey = null;
        this.firstSubmitTime = 0;
        this.intervalSeconds = 0;
    }

    /**
     * 构造防重复提交异常
     * 
     * @param message 异常消息
     * @param duplicateKey 防重复键
     * @param firstSubmitTime 首次提交时间戳
     * @param intervalSeconds 防重复时间间隔（秒）
     */
    public DuplicateSubmitException(String message, String duplicateKey, 
                                   long firstSubmitTime, int intervalSeconds) {
        super(message, "DUPLICATE_SUBMIT_DETECTED");
        this.duplicateKey = duplicateKey;
        this.firstSubmitTime = firstSubmitTime;
        this.intervalSeconds = intervalSeconds;
    }

    /**
     * 构造防重复提交异常
     * 
     * @param message 异常消息
     * @param cause 原因异常
     */
    public DuplicateSubmitException(String message, Throwable cause) {
        super(message, "DUPLICATE_SUBMIT_DETECTED", cause);
        this.duplicateKey = null;
        this.firstSubmitTime = 0;
        this.intervalSeconds = 0;
    }

    /**
     * 获取防重复键
     * 
     * @return 防重复键
     */
    public String getDuplicateKey() {
        return duplicateKey;
    }

    /**
     * 获取首次提交时间戳
     * 
     * @return 首次提交时间戳
     */
    public long getFirstSubmitTime() {
        return firstSubmitTime;
    }

    /**
     * 获取防重复时间间隔（秒）
     * 
     * @return 时间间隔
     */
    public int getIntervalSeconds() {
        return intervalSeconds;
    }
    
    /**
     * 便捷方法：获取间隔秒数（与 getIntervalSeconds 相同）
     */
    public int getInterval() {
        return intervalSeconds;
    }

    /**
     * 获取距离首次提交的时间间隔（毫秒）
     * 
     * @return 时间间隔
     */
    public long getTimeSinceFirstSubmit() {
        if (firstSubmitTime <= 0) {
            return 0;
        }
        return System.currentTimeMillis() - firstSubmitTime;
    }

    /**
     * 获取建议的重试时间（秒）
     * 
     * @return 建议重试时间，如果已过期返回0
     */
    public long getRetryAfterSeconds() {
        if (firstSubmitTime <= 0 || intervalSeconds <= 0) {
            return 0;
        }
        
        long elapsedSeconds = getTimeSinceFirstSubmit() / 1000;
        long retryAfter = intervalSeconds - elapsedSeconds;
        
        return Math.max(0, retryAfter);
    }

    /**
     * 检查是否已过期（可以重试）
     * 
     * @return true如果已过期，false如果仍在防重复期内
     */
    public boolean isExpired() {
        return getRetryAfterSeconds() <= 0;
    }
    
    @Override
    public String getErrorType() {
        return "DUPLICATE_SUBMIT";
    }
    
    @Override
    public int getHttpStatus() {
        return 429; // Too Many Requests
    }
    
    @Override
    public boolean shouldLogError() {
        // 重复提交通常不需要记录错误日志，只需要警告
        return false;
    }
}
package com.twjgs.ratelimiter.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 幂等性记录
 * 
 * <p>存储幂等性检查的相关信息，包括请求的执行状态和结果。
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IdempotentRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 幂等键
     */
    private String key;

    /**
     * 方法签名
     */
    private String methodSignature;

    /**
     * 请求参数的Hash值
     */
    private String parametersHash;

    /**
     * 用户ID（可选）
     */
    private String userId;

    /**
     * 第一次请求时间
     */
    private LocalDateTime firstRequestTime;

    /**
     * 请求执行状态
     */
    private ExecutionStatus status;

    /**
     * 执行结果（序列化后的JSON）
     */
    private String result;

    /**
     * 错误信息（如果执行失败）
     */
    private String errorMessage;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 最后访问时间
     */
    private LocalDateTime lastAccessTime;

    /**
     * 访问次数
     */
    private Integer accessCount;

    /**
     * 执行状态枚举
     */
    public enum ExecutionStatus {
        /**
         * 执行中
         */
        EXECUTING,

        /**
         * 执行成功
         */
        SUCCESS,

        /**
         * 执行失败
         */
        FAILED
    }

    /**
     * 检查记录是否已过期
     * 
     * @return true如果已过期，false如果未过期
     */
    public boolean isExpired() {
        return expireTime != null && LocalDateTime.now().isAfter(expireTime);
    }

    /**
     * 检查记录是否正在执行中
     * 
     * @return true如果正在执行，false如果已完成
     */
    public boolean isExecuting() {
        return ExecutionStatus.EXECUTING.equals(status);
    }

    /**
     * 检查记录是否执行成功
     * 
     * @return true如果执行成功，false如果未成功
     */
    public boolean isSuccess() {
        return ExecutionStatus.SUCCESS.equals(status);
    }

    /**
     * 检查记录是否执行失败
     * 
     * @return true如果执行失败，false如果未失败
     */
    public boolean isFailed() {
        return ExecutionStatus.FAILED.equals(status);
    }

    /**
     * 更新最后访问时间
     */
    public void updateLastAccessTime() {
        this.lastAccessTime = LocalDateTime.now();
        if (this.accessCount == null) {
            this.accessCount = 1;
        } else {
            this.accessCount++;
        }
    }

    /**
     * 标记为执行中
     */
    public void markAsExecuting() {
        this.status = ExecutionStatus.EXECUTING;
        this.createTime = LocalDateTime.now();
        updateLastAccessTime();
    }

    /**
     * 标记为执行成功
     * 
     * @param result 执行结果
     */
    public void markAsSuccess(String result) {
        this.status = ExecutionStatus.SUCCESS;
        this.result = result;
        updateLastAccessTime();
    }

    /**
     * 标记为执行失败
     * 
     * @param errorMessage 错误信息
     */
    public void markAsFailed(String errorMessage) {
        this.status = ExecutionStatus.FAILED;
        this.errorMessage = errorMessage;
        updateLastAccessTime();
    }
}
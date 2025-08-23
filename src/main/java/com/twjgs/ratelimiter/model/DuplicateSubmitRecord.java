package com.twjgs.ratelimiter.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 防重复提交记录
 * 
 * <p>存储防重复提交的相关信息。
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DuplicateSubmitRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 防重复键
     */
    private String key;

    /**
     * 方法签名
     */
    private String methodSignature;

    /**
     * 用户ID（可选）
     */
    private String userId;

    /**
     * 客户端IP
     */
    private String clientIp;

    /**
     * 会话ID（可选）
     */
    private String sessionId;

    /**
     * 首次提交时间
     */
    private LocalDateTime firstSubmitTime;

    /**
     * 防重复时间间隔（秒）
     */
    private Integer intervalSeconds;

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
     * 访问次数（重复提交次数）
     */
    private Integer accessCount;

    /**
     * 键生成维度
     */
    private String dimension;

    /**
     * 请求URI
     */
    private String requestUri;

    /**
     * HTTP方法
     */
    private String httpMethod;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * 检查记录是否已过期
     * 
     * @return true如果已过期，false如果未过期
     */
    public boolean isExpired() {
        return expireTime != null && LocalDateTime.now().isAfter(expireTime);
    }

    /**
     * 检查是否在防重复期内
     * 
     * @return true如果在防重复期内，false如果已过期
     */
    public boolean isInDuplicateInterval() {
        return !isExpired();
    }

    /**
     * 更新最后访问时间和访问次数
     */
    public void updateLastAccess() {
        this.lastAccessTime = LocalDateTime.now();
        if (this.accessCount == null) {
            this.accessCount = 1;
        } else {
            this.accessCount++;
        }
    }

    /**
     * 获取距离首次提交的秒数
     * 
     * @return 距离首次提交的秒数
     */
    public long getSecondsSinceFirstSubmit() {
        if (firstSubmitTime == null) {
            return 0;
        }
        return java.time.Duration.between(firstSubmitTime, LocalDateTime.now()).getSeconds();
    }

    /**
     * 获取建议的重试时间（秒）
     * 
     * @return 建议重试时间，如果已过期返回0
     */
    public long getRetryAfterSeconds() {
        if (intervalSeconds == null || intervalSeconds <= 0) {
            return 0;
        }
        
        long elapsedSeconds = getSecondsSinceFirstSubmit();
        long retryAfter = intervalSeconds - elapsedSeconds;
        
        return Math.max(0, retryAfter);
    }

    /**
     * 创建防重复提交记录
     * 
     * @param key 防重复键
     * @param methodSignature 方法签名
     * @param userId 用户ID
     * @param clientIp 客户端IP
     * @param sessionId 会话ID
     * @param intervalSeconds 防重复时间间隔（秒）
     * @param dimension 键生成维度
     * @param requestUri 请求URI
     * @param httpMethod HTTP方法
     * @param userAgent User-Agent
     * @return 防重复提交记录
     */
    public static DuplicateSubmitRecord create(String key, String methodSignature,
                                              String userId, String clientIp, String sessionId,
                                              int intervalSeconds, String dimension,
                                              String requestUri, String httpMethod, String userAgent) {
        LocalDateTime now = LocalDateTime.now();
        
        return DuplicateSubmitRecord.builder()
            .key(key)
            .methodSignature(methodSignature)
            .userId(userId)
            .clientIp(clientIp)
            .sessionId(sessionId)
            .firstSubmitTime(now)
            .intervalSeconds(intervalSeconds)
            .expireTime(now.plusSeconds(intervalSeconds))
            .createTime(now)
            .lastAccessTime(now)
            .accessCount(1)
            .dimension(dimension)
            .requestUri(requestUri)
            .httpMethod(httpMethod)
            .userAgent(userAgent)
            .build();
    }
}
package com.twjgs.ratelimiter.service;

import com.twjgs.ratelimiter.model.DuplicateSubmitRecord;

/**
 * 防重复提交服务接口
 * 
 * <p>提供防重复提交检查和记录管理功能。
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
public interface DuplicateSubmitService {

    /**
     * 检查是否为重复提交
     * 
     * <p>如果是首次提交，创建记录并返回null；
     * 如果是重复提交，返回已存在的记录。
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
     * @return 如果是重复提交返回已存在的记录，否则返回null
     */
    DuplicateSubmitRecord checkDuplicate(String key, String methodSignature,
                                        String userId, String clientIp, String sessionId,
                                        int intervalSeconds, String dimension,
                                        String requestUri, String httpMethod, String userAgent);

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
     * @return 创建的记录
     */
    DuplicateSubmitRecord createRecord(String key, String methodSignature,
                                      String userId, String clientIp, String sessionId,
                                      int intervalSeconds, String dimension,
                                      String requestUri, String httpMethod, String userAgent);

    /**
     * 获取防重复提交记录
     * 
     * @param key 防重复键
     * @return 防重复提交记录，如果不存在返回null
     */
    DuplicateSubmitRecord getRecord(String key);

    /**
     * 删除防重复提交记录
     * 
     * @param key 防重复键
     * @return true如果删除成功，false如果记录不存在
     */
    boolean deleteRecord(String key);

    /**
     * 检查记录是否存在
     * 
     * @param key 防重复键
     * @return true如果存在，false如果不存在
     */
    boolean existsRecord(String key);

    /**
     * 清理过期记录
     * 
     * @return 清理的记录数量
     */
    long cleanExpiredRecords();

    /**
     * 获取记录总数
     * 
     * @return 记录总数
     */
    long getRecordCount();

    /**
     * 获取指定维度的记录数量
     * 
     * @param dimension 键生成维度
     * @return 记录数量
     */
    long getRecordCountByDimension(String dimension);

    /**
     * 获取指定用户的记录数量
     * 
     * @param userId 用户ID
     * @return 记录数量
     */
    long getRecordCountByUser(String userId);
}
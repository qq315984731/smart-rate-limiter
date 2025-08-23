package com.twjgs.ratelimiter.service;

import com.twjgs.ratelimiter.model.IdempotentRecord;

/**
 * 幂等性服务接口
 * 
 * <p>提供幂等性检查和结果缓存功能。
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
public interface IdempotentService {

    /**
     * 检查并获取幂等性记录
     * 
     * <p>如果是第一次请求，创建新的执行中记录；
     * 如果是重复请求，返回已存在的记录。
     * 
     * @param key 幂等键
     * @param timeoutSeconds 超时时间（秒）
     * @return 幂等性记录，如果是第一次请求返回null
     */
    IdempotentRecord checkIdempotent(String key, int timeoutSeconds);

    /**
     * 创建执行中的幂等记录
     * 
     * @param key 幂等键
     * @param methodSignature 方法签名
     * @param parametersHash 参数Hash
     * @param userId 用户ID
     * @param timeoutSeconds 超时时间（秒）
     * @return 创建的幂等记录
     */
    IdempotentRecord createExecutingRecord(String key, String methodSignature, 
                                          String parametersHash, String userId, 
                                          int timeoutSeconds);

    /**
     * 标记执行成功并缓存结果
     * 
     * @param key 幂等键
     * @param result 执行结果（序列化后的JSON）
     */
    void markSuccess(String key, String result);

    /**
     * 标记执行失败
     * 
     * @param key 幂等键
     * @param errorMessage 错误信息
     */
    void markFailed(String key, String errorMessage);

    /**
     * 获取幂等记录
     * 
     * @param key 幂等键
     * @return 幂等记录，如果不存在返回null
     */
    IdempotentRecord getRecord(String key);

    /**
     * 删除幂等记录
     * 
     * @param key 幂等键
     * @return true如果删除成功，false如果记录不存在
     */
    boolean deleteRecord(String key);

    /**
     * 检查记录是否存在
     * 
     * @param key 幂等键
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
}
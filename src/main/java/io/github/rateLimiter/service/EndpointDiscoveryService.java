package io.github.rateLimiter.service;

import io.github.rateLimiter.model.EndpointInfo;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 接口发现服务接口
 * 
 * @author RateLimiter Team
 * @since 1.0.0
 */
public interface EndpointDiscoveryService {
    
    /**
     * 发现所有接口
     * 
     * @return 接口信息列表
     */
    List<EndpointInfo> discoverAllEndpoints();
    
    /**
     * 根据方法签名获取接口信息
     * 
     * @param methodSignature 方法签名
     * @return 接口信息
     */
    EndpointInfo getEndpointByMethodSignature(String methodSignature);
    
    /**
     * 搜索接口
     * 
     * @param keyword 关键字
     * @return 接口信息列表
     */
    List<EndpointInfo> searchEndpoints(String keyword);
    
    /**
     * 获取统计信息
     * 
     * @return 统计信息
     */
    Map<String, Object> getStatistics();
    
    /**
     * 获取所有路径模式
     * 
     * @return 路径模式集合
     */
    Set<String> getAllPathPatterns();
    
    /**
     * 刷新接口发现缓存
     */
    void refreshCache();
}
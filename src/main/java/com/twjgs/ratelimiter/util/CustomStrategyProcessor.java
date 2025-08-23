package com.twjgs.ratelimiter.util;

import com.twjgs.ratelimiter.annotation.Idempotent;
import com.twjgs.ratelimiter.model.IdempotentRecord;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * 统一的自定义策略处理器
 * 
 * <p>整合所有注解的CUSTOM策略处理逻辑，提供一致的验证、执行和错误处理。
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
@Slf4j
public class CustomStrategyProcessor {
    
    private final SpelExpressionEvaluator spelEvaluator;
    
    public CustomStrategyProcessor(SpelExpressionEvaluator spelEvaluator) {
        this.spelEvaluator = spelEvaluator;
    }
    
    /**
     * 处理幂等性自定义键生成
     * 
     * @param keyExpression 自定义键表达式
     * @param request HTTP请求对象
     * @param userId 用户ID
     * @param clientIp 客户端IP
     * @param methodSignature 方法签名
     * @return 生成的自定义键
     * @throws IllegalArgumentException 如果表达式无效或执行失败
     */
    public String processIdempotentCustomKey(String keyExpression,
                                           HttpServletRequest request,
                                           String userId,
                                           String clientIp,
                                           String methodSignature) {
        return processCustomKey("幂等性", keyExpression, request, userId, clientIp, methodSignature);
    }
    
    /**
     * 处理防重复提交自定义键生成
     * 
     * @param keyExpression 自定义键表达式
     * @param request HTTP请求对象
     * @param userId 用户ID
     * @param clientIp 客户端IP
     * @param methodSignature 方法签名
     * @return 生成的自定义键
     * @throws IllegalArgumentException 如果表达式无效或执行失败
     */
    public String processDuplicateSubmitCustomKey(String keyExpression,
                                                HttpServletRequest request,
                                                String userId,
                                                String clientIp,
                                                String methodSignature) {
        return processCustomKey("防重复提交", keyExpression, request, userId, clientIp, methodSignature);
    }
    
    /**
     * 处理限流自定义键生成
     * 
     * @param keyExpression 自定义键表达式
     * @param request HTTP请求对象
     * @param userId 用户ID
     * @param clientIp 客户端IP
     * @param methodSignature 方法签名
     * @return 生成的自定义键
     * @throws IllegalArgumentException 如果表达式无效或执行失败
     */
    public String processRateLimitCustomKey(String keyExpression,
                                          HttpServletRequest request,
                                          String userId,
                                          String clientIp,
                                          String methodSignature) {
        return processCustomKey("限流", keyExpression, request, userId, clientIp, methodSignature);
    }
    
    /**
     * 检测请求是否失败
     * 
     * @param exception 捕获的异常
     * @param response HTTP响应对象
     * @param failureDetection 失败检测策略
     * @param failureExceptions 指定的失败异常类型
     * @param failureCondition 自定义失败检测条件
     * @return true表示失败，false表示不是失败
     */
    public boolean isRequestFailure(Exception exception,
                                  jakarta.servlet.http.HttpServletResponse response,
                                  Idempotent.FailureDetection failureDetection,
                                  Class<? extends Throwable>[] failureExceptions,
                                  String failureCondition) {
        
        // 简单的失败检测逻辑
        if (exception == null) {
            // 没有异常，检查HTTP状态码
            if (response != null) {
                int statusCode = response.getStatus();
                return statusCode < 200 || statusCode >= 300;
            }
            return false;
        }
        
        // 根据策略检测失败
        switch (failureDetection) {
            case ALL:
                return true; // 所有异常都是失败
                
            case RUNTIME_EXCEPTION:
                return exception instanceof RuntimeException;
                
            case SPECIFIC_EXCEPTIONS:
                if (failureExceptions == null || failureExceptions.length == 0) {
                    return false;
                }
                return Arrays.stream(failureExceptions)
                    .anyMatch(exceptionType -> exceptionType.isAssignableFrom(exception.getClass()));
                
            case CUSTOM_CONDITION:
                if (!StringUtils.hasText(failureCondition)) {
                    return false;
                }
                try {
                    Object result = spelEvaluator.evaluateFailureCondition(
                        failureCondition, exception, response, 
                        response != null ? response.getStatus() : null);
                    return Boolean.TRUE.equals(result);
                } catch (Exception e) {
                    log.error("Failed to evaluate custom failure condition: {}", e.getMessage());
                    return true; // 保守处理
                }
                
            default:
                return true;
        }
    }
    
    /**
     * 统一的自定义键处理逻辑
     */
    private String processCustomKey(String context,
                                  String keyExpression,
                                  HttpServletRequest request,
                                  String userId,
                                  String clientIp,
                                  String methodSignature) {
        
        if (!StringUtils.hasText(keyExpression)) {
            throw new IllegalArgumentException(context + "配置错误: CUSTOM策略需要指定keyExpression，方法: " + methodSignature);
        }
        
        try {
            String result = spelEvaluator.evaluateExpression(keyExpression, request, userId, clientIp, methodSignature);
            
            // 验证结果有效性
            if (result == null || result.trim().isEmpty()) {
                log.warn("{}配置警告: 自定义表达式 '{}' 产生了空结果，方法: {}, 使用方法签名作为后备", 
                    context, keyExpression, methodSignature);
                return methodSignature;
            }
            
            log.debug("{} custom key generated successfully: {} -> {}", context, keyExpression, result);
            return result;
            
        } catch (Exception e) {
            log.error("{}配置错误: 自定义表达式执行失败 - 方法: {}, 表达式: '{}', 错误: {}", 
                context, methodSignature, keyExpression, e.getMessage(), e);
            throw new IllegalArgumentException(context + "配置错误: 自定义表达式执行失败: " + e.getMessage(), e);
        }
    }
    
}
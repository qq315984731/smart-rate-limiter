package com.twjgs.ratelimiter.util;

import com.twjgs.ratelimiter.annotation.Idempotent;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * 失败检测器
 * 
 * <p>根据配置的失败检测策略判断请求是否"失败"，从而决定是否允许重试。</p>
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
@Slf4j
public class FailureDetector {
    
    private final SpelExpressionEvaluator spelEvaluator;
    
    public FailureDetector(SpelExpressionEvaluator spelEvaluator) {
        this.spelEvaluator = spelEvaluator;
    }
    
    /**
     * 检测请求是否失败
     * 
     * @param exception 捕获的异常
     * @param response HTTP响应对象（可能为null）
     * @param failureDetection 失败检测策略
     * @param failureExceptions 指定的失败异常类型
     * @param failureCondition 自定义失败检测条件
     * @return true表示失败，false表示不是失败
     */
    public boolean isFailure(Exception exception,
                           HttpServletResponse response,
                           Idempotent.FailureDetection failureDetection,
                           Class<? extends Throwable>[] failureExceptions,
                           String failureCondition) {
        
        if (exception == null) {
            // 没有异常，检查HTTP状态码（如果可用）
            return checkHttpFailure(response, failureDetection, failureCondition);
        }
        
        log.debug("Checking failure for exception: {}, strategy: {}", 
            exception.getClass().getSimpleName(), failureDetection);
        
        switch (failureDetection) {
            case ALL:
                // 所有异常都视为失败
                log.debug("ALL strategy: treating all exceptions as failure");
                return true;
                
            case RUNTIME_EXCEPTION:
                // 仅RuntimeException及子类视为失败
                boolean isRuntimeException = exception instanceof RuntimeException;
                log.debug("RUNTIME_EXCEPTION strategy: {} is runtime exception: {}", 
                    exception.getClass().getSimpleName(), isRuntimeException);
                return isRuntimeException;
                
            case SPECIFIC_EXCEPTIONS:
                // 仅指定的异常类型视为失败
                if (failureExceptions == null || failureExceptions.length == 0) {
                    log.warn("SPECIFIC_EXCEPTIONS strategy used but no failure exceptions specified, defaulting to false");
                    return false;
                }
                
                boolean isSpecificException = Arrays.stream(failureExceptions)
                    .anyMatch(exceptionType -> exceptionType.isAssignableFrom(exception.getClass()));
                
                log.debug("SPECIFIC_EXCEPTIONS strategy: {} matches specified exceptions: {}", 
                    exception.getClass().getSimpleName(), isSpecificException);
                return isSpecificException;
                
            case CUSTOM_CONDITION:
                // 自定义失败检测条件
                if (!StringUtils.hasText(failureCondition)) {
                    log.warn("CUSTOM_CONDITION strategy used but no failure condition specified, defaulting to false");
                    return false;
                }
                
                return evaluateCustomCondition(exception, response, failureCondition);
                
            default:
                log.warn("Unknown failure detection strategy: {}, defaulting to true", failureDetection);
                return true;
        }
    }
    
    /**
     * 检查HTTP响应失败（当没有异常时）
     */
    private boolean checkHttpFailure(HttpServletResponse response,
                                   Idempotent.FailureDetection failureDetection,
                                   String failureCondition) {
        
        // 如果没有响应对象，无法判断HTTP失败
        if (response == null) {
            return false;
        }
        
        int statusCode = response.getStatus();
        
        switch (failureDetection) {
            case ALL:
                // 默认认为非2xx状态码为失败
                boolean isHttpFailure = statusCode < 200 || statusCode >= 300;
                log.debug("HTTP failure check (ALL strategy): status {} is failure: {}", statusCode, isHttpFailure);
                return isHttpFailure;
                
            case CUSTOM_CONDITION:
                if (StringUtils.hasText(failureCondition)) {
                    return evaluateCustomCondition(null, response, failureCondition);
                }
                break;
                
            default:
                // 其他策略不检查HTTP状态码
                return false;
        }
        
        return false;
    }
    
    /**
     * 评估自定义失败条件
     */
    private boolean evaluateCustomCondition(Exception exception,
                                          HttpServletResponse response,
                                          String failureCondition) {
        try {
            // 创建评估上下文
            Object result = spelEvaluator.evaluateFailureCondition(
                failureCondition, exception, response, 
                response != null ? response.getStatus() : null);
            
            // 转换为boolean
            boolean isFailure = Boolean.TRUE.equals(result);
            
            log.debug("Custom failure condition '{}' evaluated to: {}", failureCondition, isFailure);
            return isFailure;
            
        } catch (Exception e) {
            log.error("Failed to evaluate custom failure condition '{}': {}", 
                failureCondition, e.getMessage(), e);
            // 评估失败时，保守地认为是失败
            return true;
        }
    }
    
    /**
     * 简化版本：仅基于异常检测失败
     * 
     * <p>这是最常用的版本，适用于大多数场景。</p>
     * 
     * @param exception 捕获的异常
     * @return true表示失败，false表示不是失败
     */
    public boolean isFailure(Exception exception) {
        return isFailure(exception, null, Idempotent.FailureDetection.ALL, null, null);
    }
}
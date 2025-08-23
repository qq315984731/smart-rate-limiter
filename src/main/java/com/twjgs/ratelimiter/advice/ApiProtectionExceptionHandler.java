package com.twjgs.ratelimiter.advice;

import com.twjgs.ratelimiter.exception.ApiProtectionException;
import com.twjgs.ratelimiter.exception.RateLimitException;
import com.twjgs.ratelimiter.exception.IdempotentException;
import com.twjgs.ratelimiter.exception.DuplicateSubmitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * API保护异常全局处理器
 * 统一处理所有API保护相关的异常，返回合适的HTTP状态码和响应格式
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
@ControllerAdvice
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "smart", name = {"rate-limiter.enabled", "api-protection.enabled"}, matchIfMissing = false)
public class ApiProtectionExceptionHandler {

    /**
     * 处理限流异常
     */
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitException(RateLimitException ex) {
        if (ex.shouldLogError()) {
            log.warn("Rate limit exceeded: {}", ex.getMessage());
        }
        
        Map<String, Object> response = createErrorResponse(ex);
        
        // 添加限流特有的信息
        if (ex.hasDetailedInfo()) {
            response.put("retryAfter", ex.getRetryAfterSeconds());
            response.put("limit", ex.getPermits());
            response.put("window", ex.getWindowSeconds());
            response.put("algorithm", ex.getAlgorithm());
        }
        
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    /**
     * 处理幂等性异常
     */
    @ExceptionHandler(IdempotentException.class)
    public ResponseEntity<Map<String, Object>> handleIdempotentException(IdempotentException ex) {
        if (ex.shouldLogError()) {
            log.warn("Idempotent operation failed: {}", ex.getMessage());
        }
        
        Map<String, Object> response = createErrorResponse(ex);
        
        // 添加幂等性特有的信息
        response.put("idempotentKey", ex.getIdempotentKey());
        response.put("firstRequestTime", ex.getFirstRequestTime());
        
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    /**
     * 处理重复提交异常
     */
    @ExceptionHandler(DuplicateSubmitException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateSubmitException(DuplicateSubmitException ex) {
        if (ex.shouldLogError()) {
            log.warn("Duplicate submit detected: {}", ex.getMessage());
        }
        
        Map<String, Object> response = createErrorResponse(ex);
        
        // 添加重复提交特有的信息
        response.put("duplicateKey", ex.getDuplicateKey());
        response.put("firstSubmitTime", ex.getFirstSubmitTime());
        response.put("interval", ex.getInterval());
        
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    /**
     * 处理通用API保护异常
     */
    @ExceptionHandler(ApiProtectionException.class)
    public ResponseEntity<Map<String, Object>> handleApiProtectionException(ApiProtectionException ex) {
        if (ex.shouldLogError()) {
            log.error("API protection error: {}", ex.getMessage(), ex);
        }
        
        Map<String, Object> response = createErrorResponse(ex);
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    /**
     * 创建标准错误响应格式
     */
    private Map<String, Object> createErrorResponse(ApiProtectionException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", ex.getErrorCode());
        response.put("message", ex.getMessage());
        response.put("type", ex.getErrorType());
        response.put("timestamp", ex.getTimestamp());
        
        return response;
    }
}
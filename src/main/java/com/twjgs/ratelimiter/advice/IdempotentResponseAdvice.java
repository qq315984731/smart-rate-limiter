package com.twjgs.ratelimiter.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twjgs.ratelimiter.service.IdempotentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 幂等性响应增强器
 * 
 * <p>用于捕获Controller方法的响应结果并缓存到幂等性存储中。
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
@ControllerAdvice
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdempotentResponseAdvice implements ResponseBodyAdvice<Object> {

    private static final String IDEMPOTENT_KEY_ATTRIBUTE = "IDEMPOTENT_KEY";
    private static final String IDEMPOTENT_RECORD_ATTRIBUTE = "IDEMPOTENT_RECORD";
    
    private final IdempotentService idempotentService;
    private final ObjectMapper objectMapper;

    public IdempotentResponseAdvice(IdempotentService idempotentService,
                                   ObjectMapper objectMapper) {
        this.idempotentService = idempotentService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 只处理有幂等键的请求（即通过了IdempotentInterceptor的请求）
        HttpServletRequest request = getCurrentRequest();
        return request != null && request.getAttribute(IDEMPOTENT_KEY_ATTRIBUTE) != null;
    }

    @Override
    public Object beforeBodyWrite(Object body, 
                                MethodParameter returnType, 
                                MediaType selectedContentType,
                                Class<? extends HttpMessageConverter<?>> selectedConverterType, 
                                ServerHttpRequest serverRequest, 
                                ServerHttpResponse serverResponse) {
        
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return body;
        }
        
        String idempotentKey = (String) request.getAttribute(IDEMPOTENT_KEY_ATTRIBUTE);
        
        if (idempotentKey != null && body != null) {
            try {
                // 将响应结果序列化为JSON并缓存
                String responseJson = objectMapper.writeValueAsString(body);
                idempotentService.markSuccess(idempotentKey, responseJson);
                
                log.debug("Cached idempotent response for key: {}, response: {}", 
                    idempotentKey, responseJson.length() > 200 ? 
                        responseJson.substring(0, 200) + "..." : responseJson);
                        
            } catch (Exception e) {
                // 序列化失败时，仍然标记为成功但不缓存结果
                log.warn("Failed to serialize response for idempotent key: {}, error: {}", 
                    idempotentKey, e.getMessage());
                idempotentService.markSuccess(idempotentKey, null);
            }
        }
        
        return body;
    }

    /**
     * 获取当前请求的HttpServletRequest
     */
    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attributes.getRequest();
        } catch (Exception e) {
            log.debug("Unable to get current request: {}", e.getMessage());
            return null;
        }
    }
}
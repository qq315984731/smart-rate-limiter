package com.twjgs.ratelimiter.interceptor;

import com.twjgs.ratelimiter.annotation.DuplicateSubmit;
import com.twjgs.ratelimiter.exception.DuplicateSubmitException;
import com.twjgs.ratelimiter.model.DuplicateSubmitConfig;
import com.twjgs.ratelimiter.model.DuplicateSubmitRecord;
import com.twjgs.ratelimiter.service.DuplicateSubmitConfigService;
import com.twjgs.ratelimiter.service.DuplicateSubmitService;
import com.twjgs.ratelimiter.service.UserIdResolver;
import com.twjgs.ratelimiter.util.SpelExpressionEvaluator;
import com.twjgs.ratelimiter.util.CustomStrategyProcessor;
import com.twjgs.ratelimiter.util.IpUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 防重复提交拦截器
 * 
 * <p>通过拦截带有 @DuplicateSubmit 注解的方法，实现防重复提交功能。
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
@Slf4j
public class DuplicateSubmitInterceptor implements HandlerInterceptor, Ordered {
    
    private final DuplicateSubmitService duplicateSubmitService;
    private final DuplicateSubmitConfigService duplicateSubmitConfigService;
    private final SpelExpressionEvaluator expressionEvaluator;
    private final CustomStrategyProcessor customStrategyProcessor;
    private final UserIdResolver userIdResolver;
    private final int order;
    
    private static final String UNKNOWN_USER = "ANONYMOUS";
    private static final String UNKNOWN_IP = "0.0.0.0";
    private static final int DEFAULT_ORDER = 200;
    
    public DuplicateSubmitInterceptor(DuplicateSubmitService duplicateSubmitService,
                                     DuplicateSubmitConfigService duplicateSubmitConfigService,
                                     SpelExpressionEvaluator expressionEvaluator,
                                     CustomStrategyProcessor customStrategyProcessor,
                                     UserIdResolver userIdResolver,
                                     int order) {
        this.duplicateSubmitService = duplicateSubmitService;
        this.duplicateSubmitConfigService = duplicateSubmitConfigService;
        this.expressionEvaluator = expressionEvaluator;
        this.customStrategyProcessor = customStrategyProcessor;
        this.userIdResolver = userIdResolver;
        this.order = order;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, 
                           Object handler) throws Exception {
        
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        
        // 生成方法签名用于查找动态配置
        String methodSignature = getMethodSignature(handlerMethod);
        
        // 优先检查动态配置
        DuplicateSubmitConfig dynamicConfig = null;
        if (duplicateSubmitConfigService != null) {
            dynamicConfig = duplicateSubmitConfigService.getConfig(methodSignature);
        }
        
        DuplicateSubmit annotation = null;
        boolean isDynamicConfig = false;
        
        if (dynamicConfig != null && dynamicConfig.isEnabled()) {
            // 使用动态配置
            isDynamicConfig = true;
        } else {
            // 检查注解配置
            annotation = handlerMethod.getMethodAnnotation(DuplicateSubmit.class);
            if (annotation == null || !annotation.enabled()) {
                return true;
            }
        }
        
        // 获取请求信息
        String userId = getUserId(request);
        String clientIp = getClientIp(request);
        String sessionId = getSessionId(request);
        String userAgent = request.getHeader("User-Agent");
        String requestUri = request.getRequestURI();
        String httpMethod = request.getMethod();
        
        // 根据配置类型生成防重复键和获取参数
        String duplicateKey;
        int interval;
        String dimension;
        String message;
        
        if (isDynamicConfig) {
            duplicateKey = generateDuplicateKeyFromConfig(dynamicConfig, handlerMethod, request, 
                userId, clientIp, sessionId);
            interval = dynamicConfig.getInterval();
            dimension = dynamicConfig.getDimension();
            message = dynamicConfig.getMessage();
        } else {
            duplicateKey = generateDuplicateKey(annotation, handlerMethod, request, 
                userId, clientIp, sessionId);
            interval = annotation.interval();
            dimension = annotation.dimension().name();
            message = annotation.message();
        }
        
        // 检查重复提交
        DuplicateSubmitRecord existingRecord = duplicateSubmitService.checkDuplicate(
            duplicateKey, 
            methodSignature,
            userId,
            clientIp,
            sessionId,
            interval,
            dimension,
            requestUri,
            httpMethod,
            userAgent
        );
        
        if (existingRecord != null) {
            // 发生重复提交
            log.warn("Duplicate submit detected: key={}, user={}, ip={}, accessCount={}", 
                duplicateKey, userId, clientIp, existingRecord.getAccessCount());
            
            throw new DuplicateSubmitException(
                message,
                duplicateKey,
                existingRecord.getFirstSubmitTime()
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                interval
            );
        }
        
        
        return true;
    }

    /**
     * 生成防重复键
     */
    private String generateDuplicateKey(DuplicateSubmit annotation, HandlerMethod handlerMethod,
                                       HttpServletRequest request, String userId, 
                                       String clientIp, String sessionId) {
        
        String methodSignature = getMethodSignature(handlerMethod);
        
        switch (annotation.dimension()) {
            case USER_METHOD:
                return "user_method:" + userId + ":" + methodSignature;
                
            case IP_METHOD:
                return "ip_method:" + clientIp + ":" + methodSignature;
                
            case SESSION_METHOD:
                return "session_method:" + sessionId + ":" + methodSignature;
                
            case GLOBAL_METHOD:
                return "global_method:" + methodSignature;
                
            case CUSTOM:
                String customKey = customStrategyProcessor.processDuplicateSubmitCustomKey(
                    annotation.keyExpression(), request, userId, clientIp, methodSignature);
                return "custom:" + customKey;
                
            default:
                return "user_method:" + userId + ":" + methodSignature;
        }
    }

    /**
     * 获取方法签名
     */
    private String getMethodSignature(HandlerMethod handlerMethod) {
        return handlerMethod.getMethod().getDeclaringClass().getSimpleName() + 
               "." + handlerMethod.getMethod().getName();
    }

    /**
     * 获取用户ID
     */
    private String getUserId(HttpServletRequest request) {
        String userId = userIdResolver.resolveUserId(request);
        return StringUtils.hasText(userId) ? userId : UNKNOWN_USER;
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        return IpUtils.getClientIp(request);
    }

    /**
     * 获取会话ID
     */
    private String getSessionId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null ? session.getId() : "NO_SESSION";
    }
    
    /**
     * 根据动态配置生成防重复键
     */
    private String generateDuplicateKeyFromConfig(DuplicateSubmitConfig config, HandlerMethod handlerMethod,
                                                HttpServletRequest request, String userId, 
                                                String clientIp, String sessionId) {
        
        String methodSignature = getMethodSignature(handlerMethod);
        String dimension = config.getDimension() != null ? config.getDimension() : "USER_METHOD";
        
        switch (dimension) {
            case "USER_METHOD":
                return "user_method:" + userId + ":" + methodSignature;
            case "IP_METHOD":
                return "ip_method:" + clientIp + ":" + methodSignature;
            case "GLOBAL_METHOD":
                return "global_method:" + methodSignature;
            case "USER_PARAMS":
                // 简化处理，实际应该包含参数信息
                return "user_params:" + userId + ":" + methodSignature + ":PARAMS";
            case "IP_PARAMS":
                return "ip_params:" + clientIp + ":" + methodSignature + ":PARAMS";
            case "SESSION_METHOD":
                return "session_method:" + sessionId + ":" + methodSignature;
            case "CUSTOM":
                String customKey = customStrategyProcessor.processDuplicateSubmitCustomKey(
                    config.getKeyExpression(), request, userId, clientIp, methodSignature);
                return "custom:" + customKey;
            default:
                return "user_method:" + userId + ":" + methodSignature;
        }
    }
    
    @Override
    public int getOrder() {
        return order;
    }
}
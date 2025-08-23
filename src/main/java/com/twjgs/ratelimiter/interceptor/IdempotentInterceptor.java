package com.twjgs.ratelimiter.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twjgs.ratelimiter.annotation.Idempotent;
import com.twjgs.ratelimiter.exception.IdempotentException;
import com.twjgs.ratelimiter.model.IdempotentConfig;
import com.twjgs.ratelimiter.model.IdempotentRecord;
import com.twjgs.ratelimiter.service.IdempotentConfigService;
import com.twjgs.ratelimiter.service.IdempotentService;
import com.twjgs.ratelimiter.service.UserIdResolver;
import com.twjgs.ratelimiter.util.SpelExpressionEvaluator;
import com.twjgs.ratelimiter.util.CustomStrategyProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 幂等性拦截器
 * 
 * <p>通过拦截器实现方法级别的幂等性控制，防止重复执行相同的业务操作。
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
@Slf4j
public class IdempotentInterceptor implements HandlerInterceptor, Ordered {

    private static final String IDEMPOTENT_KEY_ATTRIBUTE = "IDEMPOTENT_KEY";
    private static final String IDEMPOTENT_RECORD_ATTRIBUTE = "IDEMPOTENT_RECORD";

    private final IdempotentService idempotentService;
    private final IdempotentConfigService idempotentConfigService;
    private final UserIdResolver userIdResolver;
    private final SpelExpressionEvaluator spelEvaluator;
    private final CustomStrategyProcessor customStrategyProcessor;
    private final ObjectMapper objectMapper;
    private final int order;

    // 缓存方法的幂等配置
    private final ConcurrentHashMap<Method, Idempotent> annotationCache = new ConcurrentHashMap<>();

    public IdempotentInterceptor(IdempotentService idempotentService,
                                 IdempotentConfigService idempotentConfigService,
                                 UserIdResolver userIdResolver,
                                 SpelExpressionEvaluator spelEvaluator,
                                 CustomStrategyProcessor customStrategyProcessor,
                                 ObjectMapper objectMapper,
                                 int order) {
        this.idempotentService = idempotentService;
        this.idempotentConfigService = idempotentConfigService;
        this.userIdResolver = userIdResolver;
        this.spelEvaluator = spelEvaluator;
        this.customStrategyProcessor = customStrategyProcessor;
        this.objectMapper = objectMapper;
        this.order = order;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, 
                           Object handler) throws Exception {
        
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();

        // 生成方法签名用于查找动态配置
        String methodSignature = getMethodSignature(method);
        
        // 优先检查动态配置
        IdempotentConfig dynamicConfig = null;
        if (idempotentConfigService != null) {
            dynamicConfig = idempotentConfigService.getConfig(methodSignature);
        }
        
        Idempotent idempotent = null;
        boolean isDynamicConfig = false;
        
        if (dynamicConfig != null && dynamicConfig.isEnabled()) {
            // 使用动态配置
            isDynamicConfig = true;
        } else {
            // 检查注解配置
            idempotent = getIdempotentAnnotation(method);
            if (idempotent == null) {
                return true;
            }
        }

        try {
            // 生成幂等键
            String idempotentKey;
            int timeout;
            boolean returnFirstResult;
            String message;
            
            if (isDynamicConfig) {
                idempotentKey = generateIdempotentKeyFromConfig(request, method, handlerMethod, dynamicConfig);
                timeout = dynamicConfig.getTimeout();
                returnFirstResult = dynamicConfig.isReturnFirstResult();
                message = dynamicConfig.getMessage();
            } else {
                idempotentKey = generateIdempotentKey(request, method, handlerMethod, idempotent);
                timeout = idempotent.timeout();
                returnFirstResult = idempotent.returnFirstResult();
                message = idempotent.message();
            }
            
            request.setAttribute(IDEMPOTENT_KEY_ATTRIBUTE, idempotentKey);

            // 检查幂等性
            IdempotentRecord existingRecord = idempotentService.checkIdempotent(idempotentKey, timeout);
            
            if (existingRecord != null) {
                // 发现重复请求
                if (isDynamicConfig) {
                    handleDuplicateRequestFromConfig(request, response, existingRecord, returnFirstResult, message, dynamicConfig);
                } else {
                    handleDuplicateRequest(request, response, existingRecord, idempotent);
                }
                return false; // 阻止继续执行
            }

            // 创建执行中的幂等记录
            String parametersHash = generateParametersHash(request);
            // 简化逻辑：键生成时已经处理了用户维度，这里统一获取userId用于记录
            String userId = userIdResolver.resolveUserId(request);

            IdempotentRecord executingRecord = idempotentService.createExecutingRecord(
                idempotentKey, methodSignature, parametersHash, userId, timeout);

            if (executingRecord == null) {
                // 创建失败，可能是并发情况下其他请求已经创建
                existingRecord = idempotentService.checkIdempotent(idempotentKey, timeout);
                if (existingRecord != null) {
                    if (isDynamicConfig) {
                        handleDuplicateRequestFromConfig(request, response, existingRecord, returnFirstResult, message, dynamicConfig);
                    } else {
                        handleDuplicateRequest(request, response, existingRecord, idempotent);
                    }
                    return false;
                }
            }

            request.setAttribute(IDEMPOTENT_RECORD_ATTRIBUTE, executingRecord);
            return true;

        } catch (Exception e) {
            log.error("Idempotent check failed for method: {}", method.getName(), e);
            // 幂等检查失败时，允许请求继续执行（降级处理）
            return true;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                              Object handler, Exception ex) throws Exception {
        
        String idempotentKey = (String) request.getAttribute(IDEMPOTENT_KEY_ATTRIBUTE);
        if (idempotentKey == null) {
            return;
        }

        try {
            // 获取幂等记录以确定配置来源
            IdempotentRecord record = (IdempotentRecord) request.getAttribute(IDEMPOTENT_RECORD_ATTRIBUTE);
            
            // 使用简化的失败检测逻辑
            boolean isFailure = customStrategyProcessor.isRequestFailure(
                ex, response, 
                Idempotent.FailureDetection.ALL, // 默认失败检测策略：异常或HTTP状态码!=200
                new Class[0], // 空异常数组
                "" // 空条件表达式
            );
            
            if (isFailure) {
                // 执行失败，标记为失败
                String errorMessage = ex != null ? ex.getMessage() : "HTTP status: " + response.getStatus();
                if (errorMessage == null) {
                    errorMessage = ex != null ? ex.getClass().getSimpleName() : "Unknown error";
                }
                idempotentService.markFailed(idempotentKey, errorMessage);
            }
            // 成功情况由IdempotentResponseAdvice处理，它可以捕获到实际的响应结果
        } catch (Exception e) {
            log.error("Failed to update idempotent record after completion for key: {}", idempotentKey, e);
        }
    }

    /**
     * 处理重复请求
     */
    private void handleDuplicateRequest(HttpServletRequest request, HttpServletResponse response,
                                       IdempotentRecord record, Idempotent idempotent) throws Exception {
        
        
        // 幂等性的核心原则：根据returnFirstResult配置决定行为
        if (record.isSuccess()) {
            if (idempotent.returnFirstResult()) {
                // 返回第一次请求的缓存结果（这是幂等性的核心）
                if (record.getResult() != null) {
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(record.getResult());
                    response.getWriter().flush();
                } else {
                    // 没有缓存结果，抛出异常提示
                    String errorMessage = !idempotent.message().isEmpty() ? 
                        idempotent.message() : "请求已处理成功，但无缓存结果";
                    throw new IdempotentException(errorMessage, record.getKey(),
                        record.getFirstRequestTime() != null ? 
                            java.sql.Timestamp.valueOf(record.getFirstRequestTime()).getTime() : 0);
                }
            } else {
                // returnFirstResult = false，抛出异常阻止重复执行
                String errorMessage = !idempotent.message().isEmpty() ? 
                    idempotent.message() : "检测到重复请求，请勿重复操作";
                throw new IdempotentException(errorMessage, record.getKey(),
                    record.getFirstRequestTime() != null ? 
                        java.sql.Timestamp.valueOf(record.getFirstRequestTime()).getTime() : 0);
            }
            return;
        }
        
        // 如果请求正在处理中，根据配置决定行为
        if (record.isExecuting()) {
            if (idempotent.returnFirstResult()) {
                // 等待首次结果（可以实现轮询或长连接，这里简化为抛异常）
                String errorMessage = !idempotent.message().isEmpty() ? 
                    idempotent.message() : "请求正在处理中，请稍候再试";
                throw new IdempotentException(errorMessage, record.getKey(),
                    record.getFirstRequestTime() != null ? 
                        java.sql.Timestamp.valueOf(record.getFirstRequestTime()).getTime() : 0);
            } else {
                // 不等待，直接抛异常阻止重复执行
                String errorMessage = !idempotent.message().isEmpty() ? 
                    idempotent.message() : "请求正在处理中，请勿重复操作";
                throw new IdempotentException(errorMessage, record.getKey(),
                    record.getFirstRequestTime() != null ? 
                        java.sql.Timestamp.valueOf(record.getFirstRequestTime()).getTime() : 0);
            }
        }
        
        // 如果之前的请求失败了，根据注解配置决定是否允许重新执行
        if (record.isFailed()) {
            // 从注解中读取重试策略
            boolean allowRetryOnFailure = idempotent.allowRetryOnFailure();
            
            if (allowRetryOnFailure) {
                return; // 允许重新执行，不阻止请求继续
            } else {
                // 不允许重试，抛出包含失败信息的异常
                String errorMessage = (!idempotent.message().isEmpty() ? idempotent.message() : 
                    "上次请求执行失败") + " (原因: " + record.getErrorMessage() + ")";
                throw new IdempotentException(errorMessage, record.getKey(),
                    record.getFirstRequestTime() != null ? 
                        java.sql.Timestamp.valueOf(record.getFirstRequestTime()).getTime() : 0);
            }
        }
        
        // 其他未知状态，保守处理  
        String errorMessage = !idempotent.message().isEmpty() ? 
            idempotent.message() : "检测到重复请求，状态: " + record.getStatus();
        throw new IdempotentException(errorMessage, record.getKey(),
            record.getFirstRequestTime() != null ? 
                java.sql.Timestamp.valueOf(record.getFirstRequestTime()).getTime() : 0);
    }

    /**
     * 生成幂等键
     */
    private String generateIdempotentKey(HttpServletRequest request, Method method, 
                                        HandlerMethod handlerMethod, Idempotent idempotent) {
        
        String baseKey;
        
        switch (idempotent.keyStrategy()) {
            case CUSTOM:
                String userId = userIdResolver.resolveUserId(request);
                String methodSignature = getMethodSignature(method);
                baseKey = customStrategyProcessor.processIdempotentCustomKey(
                    idempotent.keyExpression(), request, userId, 
                    request.getRemoteAddr(), methodSignature);
                break;
                
            case USER_PARAMS:
                String userIdForParams = userIdResolver.resolveUserId(request);
                String paramsHash = generateParametersHash(request);
                baseKey = (userIdForParams != null ? userIdForParams : "anonymous") + ":" + paramsHash;
                break;
                
            case PARAMS_HASH:
            default:
                baseKey = generateParametersHash(request);
                break;
        }
        
        // 组合方法签名和基础键
        String methodSignature = getMethodSignature(method);
        return methodSignature + ":" + baseKey;
    }

    /**
     * 生成参数Hash - 基于实际请求参数值
     */
    private String generateParametersHash(HttpServletRequest request) {
        try {
            StringBuilder paramBuilder = new StringBuilder();
            
            // 处理GET参数（Query Parameters）
            if ("GET".equalsIgnoreCase(request.getMethod())) {
                String queryString = request.getQueryString();
                if (queryString != null && !queryString.isEmpty()) {
                    paramBuilder.append("query:").append(queryString);
                }
            }
            
            // 处理POST/PUT参数（Form Parameters）  
            if ("POST".equalsIgnoreCase(request.getMethod()) || "PUT".equalsIgnoreCase(request.getMethod())) {
                var parameterMap = request.getParameterMap();
                if (parameterMap != null && !parameterMap.isEmpty()) {
                    parameterMap.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey()) // 确保参数顺序一致
                        .forEach(entry -> {
                            paramBuilder.append(entry.getKey()).append("=")
                                .append(Arrays.toString(entry.getValue())).append("&");
                        });
                }
            }
            
            // 如果没有参数，返回特殊标识
            if (paramBuilder.length() == 0) {
                return "no-params";
            }
            
            // 对参数进行MD5哈希
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(paramBuilder.toString().getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
            
        } catch (Exception e) {
            log.warn("Failed to generate parameters hash", e);
            return "hash-error";
        }
    }
    
    /**
     * 生成参数Hash的重载方法（兼容旧调用）
     */
    private String generateParametersHash(org.springframework.core.MethodParameter[] parameters) {
        // 这个方法现在已废弃，因为无法获取实际参数值
        // 返回基于参数类型的简单标识
        if (parameters == null || parameters.length == 0) {
            return "no-params";
        }
        return "method-params-" + parameters.length;
    }

    /**
     * 获取方法签名
     */
    private String getMethodSignature(Method method) {
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    /**
     * 根据动态配置生成幂等键
     */
    private String generateIdempotentKeyFromConfig(HttpServletRequest request, Method method,
                                                  HandlerMethod handlerMethod, IdempotentConfig config) {
        
        String baseKey;
        // 默认策略改为USER_PARAMS（用户ID + 请求参数），这是幂等性的最佳实践
        String keyStrategy = config.getKeyStrategy() != null ? config.getKeyStrategy() : "USER_PARAMS";
        
        switch (keyStrategy) {
            case "CUSTOM":
                String userId = userIdResolver.resolveUserId(request);
                String methodSignature = getMethodSignature(method);
                baseKey = customStrategyProcessor.processIdempotentCustomKey(
                    config.getKeyExpression(), request, userId, 
                    request.getRemoteAddr(), methodSignature);
                break;
                
            case "USER_PARAMS":
            default:
                // 主流幂等性实践：用户ID + 请求参数Hash
                String userIdForParams = userIdResolver.resolveUserId(request);
                String paramsHash = generateParametersHash(request);
                baseKey = (userIdForParams != null ? userIdForParams : "anonymous") + ":" + paramsHash;
                break;
                
            case "PARAMS_HASH":
                // 仅基于参数，适合无用户上下文的场景
                baseKey = generateParametersHash(request);
                break;
        }
        
        // 组合方法签名和基础键
        String methodSignature = getMethodSignature(method);
        return methodSignature + ":" + baseKey;
    }

    /**
     * 处理来自动态配置的重复请求
     */
    private void handleDuplicateRequestFromConfig(HttpServletRequest request, HttpServletResponse response,
                                                 IdempotentRecord record, boolean returnFirstResult, 
                                                 String message, IdempotentConfig dynamicConfig) throws Exception {
        
        
        // 幂等性的核心原则：根据returnFirstResult配置决定行为
        if (record.isSuccess()) {
            if (returnFirstResult) {
                // 返回第一次请求的缓存结果（这是幂等性的核心）
                if (record.getResult() != null) {
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(record.getResult());
                    response.getWriter().flush();
                } else {
                    // 没有缓存结果，抛出异常提示
                    String errorMessage = message != null && !message.isEmpty() ? 
                        message : "请求已处理成功，但无缓存结果";
                    throw new IdempotentException(errorMessage, record.getKey(),
                        record.getFirstRequestTime() != null ? 
                            java.sql.Timestamp.valueOf(record.getFirstRequestTime()).getTime() : 0);
                }
            } else {
                // returnFirstResult = false，抛出异常阻止重复执行
                String errorMessage = message != null && !message.isEmpty() ? 
                    message : "检测到重复请求，请勿重复操作";
                throw new IdempotentException(errorMessage, record.getKey(),
                    record.getFirstRequestTime() != null ? 
                        java.sql.Timestamp.valueOf(record.getFirstRequestTime()).getTime() : 0);
            }
            return;
        }
        
        // 如果请求正在处理中，根据配置决定行为
        if ("EXECUTING".equals(record.getStatus())) {
            if (returnFirstResult) {
                // 等待首次结果（可以实现轮询或长连接，这里简化为抛异常）
                String errorMessage = message != null && !message.isEmpty() ? 
                    message : "请求正在处理中，请稍候再试";
                throw new IdempotentException(errorMessage, record.getKey(),
                    record.getFirstRequestTime() != null ? 
                        java.sql.Timestamp.valueOf(record.getFirstRequestTime()).getTime() : 0);
            } else {
                // 不等待，直接抛异常阻止重复执行
                String errorMessage = message != null && !message.isEmpty() ? 
                    message : "请求正在处理中，请勿重复操作";
                throw new IdempotentException(errorMessage, record.getKey(),
                    record.getFirstRequestTime() != null ? 
                        java.sql.Timestamp.valueOf(record.getFirstRequestTime()).getTime() : 0);
            }
        }
        
        // 如果之前的请求失败了，根据配置决定是否允许重新执行
        if (record.isFailed()) {
            // 从动态配置中读取重试策略，默认允许重试（大多数业务场景的期望）
            boolean allowRetryOnFailure = dynamicConfig != null ? dynamicConfig.isAllowRetryOnFailure() : true;
            
            if (allowRetryOnFailure) {
                return; // 允许重新执行，不阻止请求继续
            } else {
                // 不允许重试，抛出包含失败信息的异常
                String errorMessage = (message != null && !message.isEmpty() ? message : 
                    "上次请求执行失败") + " (原因: " + record.getErrorMessage() + ")";
                throw new IdempotentException(errorMessage, record.getKey(),
                    record.getFirstRequestTime() != null ? 
                        java.sql.Timestamp.valueOf(record.getFirstRequestTime()).getTime() : 0);
            }
        }
        
        // 其他未知状态，保守处理  
        String errorMessage = message != null && !message.isEmpty() ? 
            message : "检测到重复请求，状态: " + record.getStatus();
        throw new IdempotentException(errorMessage, record.getKey(),
            record.getFirstRequestTime() != null ? 
                java.sql.Timestamp.valueOf(record.getFirstRequestTime()).getTime() : 0);
    }

    /**
     * 获取幂等注解（带缓存）
     */
    private Idempotent getIdempotentAnnotation(Method method) {
        return annotationCache.computeIfAbsent(method, m -> 
            AnnotatedElementUtils.findMergedAnnotation(m, Idempotent.class));
    }
}
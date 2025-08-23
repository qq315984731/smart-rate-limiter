package com.twjgs.ratelimiter.interceptor;

import com.twjgs.ratelimiter.annotation.MultiRateLimit;
import com.twjgs.ratelimiter.annotation.RateLimit;
import com.twjgs.ratelimiter.config.RateLimiterProperties;
import com.twjgs.ratelimiter.exception.RateLimitException;
import com.twjgs.ratelimiter.model.DynamicRateLimitConfig;
import com.twjgs.ratelimiter.model.RateLimitContext;
import com.twjgs.ratelimiter.model.RateLimitResult;
import com.twjgs.ratelimiter.service.DynamicConfigService;
import com.twjgs.ratelimiter.service.IpResolver;
import com.twjgs.ratelimiter.service.RateLimitService;
import com.twjgs.ratelimiter.service.UserIdResolver;
import com.twjgs.ratelimiter.util.KeyGenerator;
import com.twjgs.ratelimiter.util.SpelExpressionEvaluator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * Core rate limiting interceptor for Spring MVC
 * 
 * <p>This interceptor handles both single and multi-dimensional rate limiting
 * based on annotations applied to controller methods.
 * 
 * @author Smart Rate Limiter Team
 * @since 1.0.0
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimitService rateLimitService;
    private final RateLimiterProperties properties;
    private final UserIdResolver userIdResolver;
    private final IpResolver ipResolver;
    private final KeyGenerator keyGenerator;
    private final SpelExpressionEvaluator spelEvaluator;
    private final DynamicConfigService dynamicConfigService;

    public RateLimitInterceptor(RateLimitService rateLimitService,
                               RateLimiterProperties properties,
                               UserIdResolver userIdResolver,
                               IpResolver ipResolver,
                               KeyGenerator keyGenerator,
                               SpelExpressionEvaluator spelEvaluator,
                               DynamicConfigService dynamicConfigService) {
        this.rateLimitService = rateLimitService;
        this.properties = properties;
        this.userIdResolver = userIdResolver;
        this.ipResolver = ipResolver;
        this.keyGenerator = keyGenerator;
        this.spelEvaluator = spelEvaluator;
        this.dynamicConfigService = dynamicConfigService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!properties.isEnabled()) {
            return true;
        }

        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();
        
        // Generate method signature for dynamic config lookup
        String methodSignature = generateMethodSignature(method);
        
        // Check for dynamic configuration first (highest priority)
        DynamicRateLimitConfig dynamicConfig = null;
        if (dynamicConfigService != null) {
            dynamicConfig = dynamicConfigService.getDynamicConfig(methodSignature);
            if (dynamicConfig != null && Boolean.TRUE.equals(dynamicConfig.getEnabled())) {
                // Check if config is expired
                if (dynamicConfig.getTemporary() && dynamicConfig.getExpireTime() != null && 
                    dynamicConfig.getExpireTime().isBefore(java.time.LocalDateTime.now())) {
                    dynamicConfigService.deleteDynamicConfig(methodSignature, "SYSTEM");
                    dynamicConfig = null;
                } else {
                    return handleDynamicRateLimit(request, response, handlerMethod, dynamicConfig);
                }
            }
        }

        // Check for MultiRateLimit annotation (second priority)
        MultiRateLimit multiRateLimit = findMultiRateLimit(method, handlerMethod.getBeanType());
        if (multiRateLimit != null) {
            return handleMultiRateLimit(request, response, handlerMethod, multiRateLimit);
        }

        // Check for single RateLimit annotation (lowest priority)
        RateLimit rateLimit = findRateLimit(method, handlerMethod.getBeanType());
        if (rateLimit != null && rateLimit.enabled()) {
            return handleSingleRateLimit(request, response, handlerMethod, rateLimit);
        }

        return true;
    }

    /**
     * Handles single rate limit annotation
     */
    private boolean handleSingleRateLimit(HttpServletRequest request, 
                                        HttpServletResponse response,
                                        HandlerMethod handlerMethod, 
                                        RateLimit rateLimit) {
        try {
            RateLimitContext context = buildContext(request, handlerMethod, rateLimit);
            RateLimitResult result = rateLimitService.checkRateLimit(context);

            if (!result.isAllowed()) {
                handleRateLimitExceeded(context, result);
                return false;
            }

            // Add rate limit headers to response
            addRateLimitHeaders(response, result);
            return true;

        } catch (RateLimitException e) {
            // Re-throw rate limit exceptions
            throw e;
        } catch (Exception e) {
            log.error("Rate limit check failed for method: {}", handlerMethod.getMethod().getName(), e);
            
            // Handle based on fallback configuration
            if (properties.getFallback().getOnError() == RateLimiterProperties.Fallback.ErrorBehavior.ALLOW) {
                log.warn("Allowing request due to rate limit error: {}", e.getMessage());
                return true;
            } else {
                throw new RateLimitException("Rate limit check failed", e);
            }
        }
    }

    /**
     * Handles multi-rate limit annotation
     */
    private boolean handleMultiRateLimit(HttpServletRequest request,
                                       HttpServletResponse response,
                                       HandlerMethod handlerMethod,
                                       MultiRateLimit multiRateLimit) {
        RateLimit[] rateLimits = multiRateLimit.value();
        if (rateLimits.length == 0) {
            return true;
        }

        boolean shortCircuit = multiRateLimit.shortCircuit();
        MultiRateLimit.CombineStrategy strategy = multiRateLimit.strategy();

        List<RateLimitResult> results = Arrays.stream(rateLimits)
                .filter(RateLimit::enabled)
                .map(rateLimit -> {
                    try {
                        RateLimitContext context = buildContext(request, handlerMethod, rateLimit);
                        return rateLimitService.checkRateLimit(context);
                    } catch (Exception e) {
                        log.error("Rate limit check failed for rule: {}", rateLimit.dimension(), e);
                        
                        // Return appropriate result based on fallback configuration
                        if (properties.getFallback().getOnError() == RateLimiterProperties.Fallback.ErrorBehavior.ALLOW) {
                            return RateLimitResult.allowed("error-fallback", 0, 0);
                        } else {
                            throw new RateLimitException("Rate limit check failed", e);
                        }
                    }
                })
                .toList();

        boolean finalResult = evaluateMultiRateLimitResults(results, strategy, shortCircuit);

        if (!finalResult) {
            // Find the first rejected result for error reporting
            RateLimitResult rejectedResult = results.stream()
                    .filter(result -> !result.isAllowed())
                    .findFirst()
                    .orElse(null);

            if (rejectedResult != null) {
                RateLimitContext errorContext = RateLimitContext.builder()
                        .customMessage(multiRateLimit.message())
                        .build();
                handleRateLimitExceeded(errorContext, rejectedResult);
            } else {
                throw new RateLimitException(multiRateLimit.message());
            }
            return false;
        }

        // Add headers from the most restrictive result
        RateLimitResult mostRestrictive = findMostRestrictiveResult(results);
        if (mostRestrictive != null) {
            addRateLimitHeaders(response, mostRestrictive);
        }

        return true;
    }

    /**
     * Builds rate limit context from request and annotation
     */
    private RateLimitContext buildContext(HttpServletRequest request, 
                                        HandlerMethod handlerMethod, 
                                        RateLimit rateLimit) {
        String userId = resolveUserId(request);
        String clientIp = resolveClientIp(request);
        String methodSignature = getMethodSignature(handlerMethod);

        String key = keyGenerator.generateKey(
                rateLimit.dimension(),
                methodSignature,
                userId,
                clientIp,
                rateLimit.keyExpression(),
                request,
                spelEvaluator
        );

        return RateLimitContext.builder()
                .key(key)
                .permits(rateLimit.permits())
                .windowSeconds(rateLimit.window())
                .algorithm(rateLimit.algorithm())
                .dimension(rateLimit.dimension())
                .strategy(rateLimit.strategy())
                .methodSignature(methodSignature)
                .userId(userId)
                .clientIp(clientIp)
                .request(request)
                .queueTimeout(rateLimit.queueTimeout())
                .customMessage(rateLimit.message())
                .bucketCapacity(rateLimit.bucketCapacity())
                .refillRate(rateLimit.refillRate())
                .build();
    }

    /**
     * Evaluates multiple rate limit results based on combination strategy
     */
    private boolean evaluateMultiRateLimitResults(List<RateLimitResult> results,
                                                MultiRateLimit.CombineStrategy strategy,
                                                boolean shortCircuit) {
        if (results.isEmpty()) {
            return true;
        }

        if (strategy == MultiRateLimit.CombineStrategy.AND) {
            // All must pass
            for (RateLimitResult result : results) {
                if (!result.isAllowed()) {
                    if (shortCircuit) {
                        return false;
                    }
                    // Continue checking for monitoring purposes
                }
            }
            return results.stream().allMatch(RateLimitResult::isAllowed);
        } else {
            // OR strategy - at least one must pass
            for (RateLimitResult result : results) {
                if (result.isAllowed()) {
                    if (shortCircuit) {
                        return true;
                    }
                    // Continue checking for monitoring purposes
                }
            }
            return results.stream().anyMatch(RateLimitResult::isAllowed);
        }
    }

    /**
     * Finds the most restrictive result (lowest remaining permits)
     */
    private RateLimitResult findMostRestrictiveResult(List<RateLimitResult> results) {
        return results.stream()
                .filter(RateLimitResult::isAllowed)
                .min((r1, r2) -> Long.compare(r1.getRemainingPermits(), r2.getRemainingPermits()))
                .orElse(null);
    }

    /**
     * Handles rate limit exceeded scenarios
     */
    private void handleRateLimitExceeded(RateLimitContext context, RateLimitResult result) {
        String message = context.getCustomMessage() != null ? 
                context.getCustomMessage() : "Request rate limit exceeded, please try again later";

        if (context.getStrategy() == RateLimit.LimitStrategy.QUEUE) {
            try {
                Thread.sleep(context.getQueueTimeout());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RateLimitException("Request timeout while waiting in queue", e);
            }
        } else {
            throw new RateLimitException(
                    message,
                    result.getKey(),
                    (int) result.getTotalPermits(),
                    context.getWindowSeconds(),
                    context.getAlgorithm().name(),
                    context.getDimension().name(),
                    result.getRetryAfterSeconds()
            );
        }
    }

    /**
     * Adds rate limit information to response headers
     */
    private void addRateLimitHeaders(HttpServletResponse response, RateLimitResult result) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.getTotalPermits()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemainingPermits()));
        
        if (result.getResetTime() != null) {
            response.setHeader("X-RateLimit-Reset", String.valueOf(result.getResetTime().getEpochSecond()));
        }
        
        if (result.getRetryAfterSeconds() != null) {
            response.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
        }
    }

    /**
     * Finds RateLimit annotation on method or class
     */
    private RateLimit findRateLimit(Method method, Class<?> targetClass) {
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);
        if (rateLimit != null) {
            return rateLimit;
        }
        return targetClass.getAnnotation(RateLimit.class);
    }

    /**
     * Finds MultiRateLimit annotation on method or class
     */
    private MultiRateLimit findMultiRateLimit(Method method, Class<?> targetClass) {
        MultiRateLimit multiRateLimit = method.getAnnotation(MultiRateLimit.class);
        if (multiRateLimit != null) {
            return multiRateLimit;
        }
        return targetClass.getAnnotation(MultiRateLimit.class);
    }

    /**
     * Resolves user ID from request
     */
    private String resolveUserId(HttpServletRequest request) {
        try {
            return userIdResolver.resolveUserId(request);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolves client IP from request
     */
    private String resolveClientIp(HttpServletRequest request) {
        try {
            return ipResolver.resolveIp(request);
        } catch (Exception e) {
            log.warn("Failed to resolve client IP: {}", e.getMessage());
            return request.getRemoteAddr();
        }
    }

    /**
     * Gets method signature for rate limiting keys
     */
    private String getMethodSignature(HandlerMethod handlerMethod) {
        if (properties.isIncludeMethodSignature()) {
            return handlerMethod.getBeanType().getName() + "." + handlerMethod.getMethod().getName();
        } else {
            return handlerMethod.getMethod().getName();
        }
    }

    /**
     * Generates method signature for dynamic config lookup
     */
    private String generateMethodSignature(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getDeclaringClass().getName()).append(".").append(method.getName()).append("(");
        
        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(paramTypes[i].getSimpleName());
        }
        
        sb.append(")");
        return sb.toString();
    }

    /**
     * Handles dynamic rate limit configuration
     */
    private boolean handleDynamicRateLimit(HttpServletRequest request, 
                                          HttpServletResponse response,
                                          HandlerMethod handlerMethod, 
                                          DynamicRateLimitConfig dynamicConfig) {
        try {
            RateLimitContext context = buildDynamicContext(request, handlerMethod, dynamicConfig);
            RateLimitResult result = rateLimitService.checkRateLimit(context);

            if (!result.isAllowed()) {
                handleRateLimitExceeded(context, result);
                return false;
            }

            addRateLimitHeaders(response, result);
            return true;

        } catch (RateLimitException e) {
            log.warn("Rate limit exceeded for dynamic config: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error applying dynamic rate limit", e);
            if (properties.getFallback().getOnError() == RateLimiterProperties.Fallback.ErrorBehavior.ALLOW) {
                return true;
            } else {
                throw new RateLimitException("Rate limiting temporarily unavailable", e);
            }
        }
    }

    /**
     * Builds rate limit context from request and dynamic configuration
     */
    private RateLimitContext buildDynamicContext(HttpServletRequest request, 
                                               HandlerMethod handlerMethod, 
                                               DynamicRateLimitConfig dynamicConfig) {
        String userId = resolveUserId(request);
        String clientIp = resolveClientIp(request);
        String methodSignature = generateMethodSignature(handlerMethod.getMethod());

        // Convert string dimension to enum
        RateLimit.LimitDimension dimension = RateLimit.LimitDimension.GLOBAL;
        if (dynamicConfig.getDimension() != null) {
            try {
                dimension = RateLimit.LimitDimension.valueOf(dynamicConfig.getDimension().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid dimension in dynamic config: {}, using GLOBAL", dynamicConfig.getDimension());
            }
        }

        // Convert algorithm string to enum
        RateLimit.LimitAlgorithm algorithm = RateLimit.LimitAlgorithm.SLIDING_WINDOW;
        if (dynamicConfig.getAlgorithm() != null) {
            try {
                algorithm = RateLimit.LimitAlgorithm.valueOf(dynamicConfig.getAlgorithm().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid algorithm in dynamic config: {}, using SLIDING_WINDOW", dynamicConfig.getAlgorithm());
            }
        }

        // Convert strategy string to enum
        RateLimit.LimitStrategy strategy = RateLimit.LimitStrategy.REJECT;
        if (dynamicConfig.getStrategy() != null) {
            try {
                strategy = RateLimit.LimitStrategy.valueOf(dynamicConfig.getStrategy().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid strategy in dynamic config: {}, using REJECT", dynamicConfig.getStrategy());
            }
        }

        String key = keyGenerator.generateKey(
                dimension,
                methodSignature,
                userId,
                clientIp,
                null, // Dynamic configs don't support key expressions yet
                request,
                spelEvaluator
        );

        return RateLimitContext.builder()
                .key(key)
                .permits(dynamicConfig.getPermits())
                .windowSeconds(dynamicConfig.getWindow())
                .algorithm(algorithm)
                .dimension(dimension)
                .strategy(strategy)
                .methodSignature(methodSignature)
                .userId(userId)
                .clientIp(clientIp)
                .request(request)
                .queueTimeout(dynamicConfig.getQueueTimeout() != null ? dynamicConfig.getQueueTimeout() : 1000L)
                .customMessage(dynamicConfig.getMessage())
                .bucketCapacity(dynamicConfig.getBucketCapacity() != null ? dynamicConfig.getBucketCapacity() : dynamicConfig.getPermits())
                .refillRate(dynamicConfig.getRefillRate() != null ? dynamicConfig.getRefillRate() : 1.0)
                .build();
    }
}
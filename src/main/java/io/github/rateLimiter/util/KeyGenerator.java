package io.github.rateLimiter.util;

import io.github.rateLimiter.annotation.RateLimit;
import io.github.rateLimiter.config.RateLimiterProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * Utility class for generating rate limit keys
 * 
 * <p>Generates unique keys for different rate limiting dimensions
 * to ensure proper isolation and conflict resolution.
 * 
 * @author Smart Rate Limiter Team
 * @since 1.0.0
 */
public class KeyGenerator {

    private final RateLimiterProperties properties;

    public KeyGenerator(RateLimiterProperties properties) {
        this.properties = properties;
    }

    /**
     * Generates a rate limit key based on the specified dimension and context
     * 
     * @param dimension the rate limiting dimension
     * @param methodSignature the method signature
     * @param userId the user ID (may be null)
     * @param clientIp the client IP address
     * @param keyExpression custom key expression for CUSTOM dimension
     * @param request the HTTP request
     * @param spelEvaluator SpEL expression evaluator
     * @return the generated rate limit key
     */
    public String generateKey(RateLimit.LimitDimension dimension,
                            String methodSignature,
                            String userId,
                            String clientIp,
                            String keyExpression,
                            HttpServletRequest request,
                            SpelExpressionEvaluator spelEvaluator) {
        
        String prefix = properties.getRedis().getKeyPrefix();
        String separator = properties.getRedis().getKeySeparator();
        
        switch (dimension) {
            case USER:
                return buildKey(prefix, "user", userId != null ? userId : "anonymous", methodSignature, separator);
                
            case IP:
                return buildKey(prefix, "ip", clientIp, methodSignature, separator);
                
            case API:
                return buildKey(prefix, "api", methodSignature, separator);
                
            case GLOBAL:
                return buildKey(prefix, "global", "system", separator);
                
            case CUSTOM:
                String customKey = evaluateCustomKey(keyExpression, request, userId, clientIp, methodSignature, spelEvaluator);
                return buildKey(prefix, "custom", customKey, separator);
                
            default:
                throw new IllegalArgumentException("Unsupported rate limit dimension: " + dimension);
        }
    }

    /**
     * Evaluates custom key expression using SpEL
     */
    private String evaluateCustomKey(String keyExpression,
                                   HttpServletRequest request,
                                   String userId,
                                   String clientIp,
                                   String methodSignature,
                                   SpelExpressionEvaluator spelEvaluator) {
        if (!StringUtils.hasText(keyExpression)) {
            // Default to method signature if no expression provided
            return methodSignature;
        }

        try {
            return spelEvaluator.evaluateExpression(keyExpression, request, userId, clientIp, methodSignature);
        } catch (Exception e) {
            // Fallback to method signature on evaluation error
            return methodSignature;
        }
    }

    /**
     * Builds a key by joining components with separator
     */
    private String buildKey(String prefix, String... components) {
        String separator = properties.getRedis().getKeySeparator();
        StringBuilder key = new StringBuilder(prefix);
        
        for (String component : components) {
            if (StringUtils.hasText(component)) {
                if (key.length() > 0 && !key.toString().endsWith(separator)) {
                    key.append(separator);
                }
                key.append(component);
            }
        }
        
        return key.toString();
    }

    /**
     * Extracts the dimension type from a rate limit key
     * 
     * @param key the rate limit key
     * @return the dimension type, or null if not determinable
     */
    public String extractDimension(String key) {
        String prefix = properties.getRedis().getKeyPrefix();
        if (!key.startsWith(prefix)) {
            return null;
        }
        
        String remaining = key.substring(prefix.length());
        String separator = properties.getRedis().getKeySeparator();
        
        int separatorIndex = remaining.indexOf(separator);
        if (separatorIndex > 0) {
            return remaining.substring(0, separatorIndex);
        }
        
        return remaining;
    }

    /**
     * Validates that a generated key is properly formatted
     * 
     * @param key the key to validate
     * @return true if the key is valid
     */
    public boolean isValidKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        
        String prefix = properties.getRedis().getKeyPrefix();
        return key.startsWith(prefix) && key.length() > prefix.length();
    }
}
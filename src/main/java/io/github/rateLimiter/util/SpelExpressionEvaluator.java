package io.github.rateLimiter.util;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

import java.util.concurrent.ConcurrentHashMap;

/**
 * SpEL (Spring Expression Language) evaluator for custom rate limit keys
 * 
 * <p>Provides a safe and cached way to evaluate SpEL expressions for custom
 * rate limiting keys. Supports various context variables and caches parsed
 * expressions for performance.
 * 
 * @author Smart Rate Limiter Team
 * @since 1.0.0
 */
public class SpelExpressionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(SpelExpressionEvaluator.class);

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ConcurrentHashMap<String, Expression> expressionCache = new ConcurrentHashMap<>();
    private final int maxCacheSize;

    public SpelExpressionEvaluator() {
        this(1000); // Default cache size
    }

    public SpelExpressionEvaluator(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
    }

    /**
     * Evaluates a SpEL expression with the given context
     * 
     * @param expression the SpEL expression to evaluate
     * @param request the HTTP request
     * @param userId the user ID (may be null)
     * @param clientIp the client IP address
     * @param methodSignature the method signature
     * @return the evaluated expression result as string
     * @throws RuntimeException if expression evaluation fails
     */
    public String evaluateExpression(String expression,
                                   HttpServletRequest request,
                                   String userId,
                                   String clientIp,
                                   String methodSignature) {
        if (!StringUtils.hasText(expression)) {
            return methodSignature; // Default fallback
        }

        try {
            Expression parsedExpression = getOrParseExpression(expression);
            StandardEvaluationContext context = createEvaluationContext(request, userId, clientIp, methodSignature);
            
            Object result = parsedExpression.getValue(context);
            return result != null ? result.toString() : methodSignature;
            
        } catch (Exception e) {
            log.warn("Failed to evaluate SpEL expression '{}': {}", expression, e.getMessage());
            return methodSignature; // Fallback to method signature
        }
    }

    /**
     * Gets a parsed expression from cache or parses and caches it
     */
    private Expression getOrParseExpression(String expressionString) {
        // Check cache first
        Expression expression = expressionCache.get(expressionString);
        if (expression != null) {
            return expression;
        }

        // Parse and cache if under limit
        if (expressionCache.size() < maxCacheSize) {
            try {
                expression = parser.parseExpression(expressionString);
                expressionCache.put(expressionString, expression);
                return expression;
            } catch (Exception e) {
                log.warn("Failed to parse SpEL expression '{}': {}", expressionString, e.getMessage());
                throw e;
            }
        } else {
            // Cache is full, parse without caching
            return parser.parseExpression(expressionString);
        }
    }

    /**
     * Creates an evaluation context with available variables
     */
    private StandardEvaluationContext createEvaluationContext(HttpServletRequest request,
                                                             String userId,
                                                             String clientIp,
                                                             String methodSignature) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        // Add request object
        if (request != null) {
            context.setVariable("request", request);
            context.setVariable("method", request.getMethod());
            context.setVariable("uri", request.getRequestURI());
            context.setVariable("userAgent", request.getHeader("User-Agent"));
            
            // Add common headers
            context.setVariable("headers", new HeaderAccessor(request));
        }

        // Add user information
        context.setVariable("userId", userId);
        context.setVariable("user", userId); // Alias for shorter expressions

        // Add IP information
        context.setVariable("ip", clientIp);
        context.setVariable("clientIp", clientIp); // Alias

        // Add method information
        context.setVariable("methodSignature", methodSignature);
        if (methodSignature != null && methodSignature.contains(".")) {
            String[] parts = methodSignature.split("\\.");
            if (parts.length >= 2) {
                context.setVariable("className", parts[0]);
                context.setVariable("methodName", parts[1]);
            }
        }

        return context;
    }

    /**
     * Clears the expression cache
     */
    public void clearCache() {
        expressionCache.clear();
    }

    /**
     * Gets the current cache size
     * 
     * @return the number of cached expressions
     */
    public int getCacheSize() {
        return expressionCache.size();
    }

    /**
     * Helper class to provide convenient access to request headers in SpEL
     */
    private static class HeaderAccessor {
        private final HttpServletRequest request;

        public HeaderAccessor(HttpServletRequest request) {
            this.request = request;
        }

        public String get(String headerName) {
            return request.getHeader(headerName);
        }

        public String getOrDefault(String headerName, String defaultValue) {
            String value = request.getHeader(headerName);
            return value != null ? value : defaultValue;
        }
    }
}
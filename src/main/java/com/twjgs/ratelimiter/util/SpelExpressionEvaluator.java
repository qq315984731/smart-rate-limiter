package com.twjgs.ratelimiter.util;

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
            // 验证表达式安全性
            validateExpressionSafety(expression);
            
            Expression parsedExpression = getOrParseExpression(expression);
            StandardEvaluationContext context = createEvaluationContext(request, userId, clientIp, methodSignature);
            
            Object result = parsedExpression.getValue(context);
            String evaluatedResult = result != null ? result.toString() : methodSignature;
            
            // 验证结果有效性
            if (!isValidKeyResult(evaluatedResult)) {
                log.warn("SpEL expression '{}' produced invalid key result: '{}'", expression, evaluatedResult);
                return methodSignature; // 返回安全的默认值
            }
            
            return evaluatedResult;
            
        } catch (Exception e) {
            log.warn("Failed to evaluate SpEL expression '{}' for method '{}': {}", 
                expression, methodSignature, e.getMessage(), e);
            return methodSignature; // Fallback to method signature
        }
    }
    
    /**
     * 验证SpEL表达式的安全性
     * 
     * @param expression SpEL表达式
     * @throws IllegalArgumentException 如果表达式不安全
     */
    private void validateExpressionSafety(String expression) {
        // 检查危险的操作符和方法调用
        String[] dangerousPatterns = {
            "T(", "new ", "Class.", "getClass(", "forName(", 
            "Runtime.", "ProcessBuilder", "System.", "Thread.",
            "@", "bean(", "environment.", "applicationContext."
        };
        
        String upperExpression = expression.toUpperCase();
        for (String pattern : dangerousPatterns) {
            if (upperExpression.contains(pattern.toUpperCase())) {
                throw new IllegalArgumentException(
                    "SpEL表达式包含不安全的操作: '" + pattern + "' in expression: " + expression);
            }
        }
        
        // 检查表达式长度
        if (expression.length() > 500) {
            throw new IllegalArgumentException("SpEL表达式过长 (超过500字符): " + expression.length());
        }
    }
    
    /**
     * 验证生成的键结果是否有效
     * 
     * @param result 键结果
     * @return true如果有效，false如果无效
     */
    private boolean isValidKeyResult(String result) {
        if (result == null || result.trim().isEmpty()) {
            return false;
        }
        
        // 检查结果长度
        if (result.length() > 200) {
            return false;
        }
        
        // 检查是否包含不安全字符（避免键注入）
        if (result.contains("\n") || result.contains("\r") || result.contains("\0")) {
            return false;
        }
        
        return true;
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
     * 评估失败处理表达式
     * 
     * @param expression SpEL表达式
     * @param record 幂等性记录
     * @param request HTTP请求对象
     * @param userId 用户ID
     * @param error 原始异常
     * @return 表达式计算结果
     */
    public Object evaluateFailureExpression(String expression,
                                          Object record,
                                          jakarta.servlet.http.HttpServletRequest request,
                                          String userId,
                                          Exception error) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }

        try {
            // 验证表达式安全性
            validateExpressionSafety(expression);
            
            Expression parsedExpression = getOrParseExpression(expression);
            StandardEvaluationContext context = createFailureEvaluationContext(record, request, userId, error);
            
            return parsedExpression.getValue(context);
            
        } catch (Exception e) {
            log.warn("Failed to evaluate failure expression '{}': {}", expression, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 创建失败处理的评估上下文
     */
    private StandardEvaluationContext createFailureEvaluationContext(Object record,
                                                                   jakarta.servlet.http.HttpServletRequest request,
                                                                   String userId,
                                                                   Exception error) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 添加失败相关变量
        context.setVariable("record", record);
        context.setVariable("error", error);
        context.setVariable("exception", error); // 别名
        
        // 添加请求相关变量
        if (request != null) {
            context.setVariable("request", request);
            context.setVariable("method", request.getMethod());
            context.setVariable("uri", request.getRequestURI());
            context.setVariable("headers", new HeaderAccessor(request));
        }

        // 添加用户信息
        context.setVariable("userId", userId);
        context.setVariable("user", userId);

        return context;
    }

    /**
     * 评估失败条件表达式
     * 
     * @param expression SpEL表达式
     * @param exception 异常对象
     * @param response HTTP响应对象
     * @param statusCode HTTP状态码
     * @return 表达式计算结果
     */
    public Object evaluateFailureCondition(String expression,
                                         Exception exception,
                                         jakarta.servlet.http.HttpServletResponse response,
                                         Integer statusCode) {
        if (!StringUtils.hasText(expression)) {
            return false;
        }

        try {
            // 验证表达式安全性
            validateExpressionSafety(expression);
            
            Expression parsedExpression = getOrParseExpression(expression);
            StandardEvaluationContext context = createFailureConditionContext(exception, response, statusCode);
            
            return parsedExpression.getValue(context);
            
        } catch (Exception e) {
            log.warn("Failed to evaluate failure condition '{}': {}", expression, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 创建失败条件的评估上下文
     */
    private StandardEvaluationContext createFailureConditionContext(Exception exception,
                                                                  jakarta.servlet.http.HttpServletResponse response,
                                                                  Integer statusCode) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 添加异常相关变量
        context.setVariable("exception", exception);
        
        // 添加HTTP响应相关变量
        context.setVariable("response", response);
        context.setVariable("statusCode", statusCode);
        
        return context;
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
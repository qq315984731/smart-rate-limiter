package com.twjgs.ratelimiter.util;

import com.twjgs.ratelimiter.exception.ConfigurationException;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 配置参数验证器
 * 统一验证各种配置参数的有效性
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
public class ConfigurationValidator {
    
    // IP地址正则表达式
    private static final Pattern IP_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );
    
    // IPv6地址简单验证
    private static final Pattern IPV6_PATTERN = Pattern.compile(
        "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^::1$|^::$"
    );
    
    // 时间间隔范围限制
    private static final int MIN_INTERVAL_SECONDS = 1;
    private static final int MAX_INTERVAL_SECONDS = 86400; // 24小时
    
    // 许可数量范围限制
    private static final int MIN_PERMITS = 1;
    private static final int MAX_PERMITS = 100000;
    
    /**
     * 验证限流间隔时间
     */
    public static void validateInterval(int interval, String paramName) {
        if (interval < MIN_INTERVAL_SECONDS || interval > MAX_INTERVAL_SECONDS) {
            throw ConfigurationException.invalidRange(
                paramName, 
                interval, 
                MIN_INTERVAL_SECONDS + " to " + MAX_INTERVAL_SECONDS + " seconds"
            );
        }
    }
    
    /**
     * 验证许可数量
     */
    public static void validatePermits(int permits, String paramName) {
        if (permits < MIN_PERMITS || permits > MAX_PERMITS) {
            throw ConfigurationException.invalidRange(
                paramName, 
                permits, 
                MIN_PERMITS + " to " + MAX_PERMITS
            );
        }
    }
    
    /**
     * 验证IP地址格式
     */
    public static void validateIpAddress(String ip, String paramName) {
        if (!StringUtils.hasText(ip)) {
            throw ConfigurationException.missingRequired(paramName);
        }
        
        String trimmedIp = ip.trim();
        
        // 特殊值允许
        if ("*".equals(trimmedIp) || "localhost".equalsIgnoreCase(trimmedIp)) {
            return;
        }
        
        // 通配符IP
        if (trimmedIp.endsWith("*")) {
            String prefix = trimmedIp.substring(0, trimmedIp.length() - 1);
            if (!IP_PATTERN.matcher(prefix + "0").matches()) {
                throw ConfigurationException.invalidFormat(paramName, ip, "valid IP address or pattern");
            }
            return;
        }
        
        // IPv4或IPv6验证
        if (!IP_PATTERN.matcher(trimmedIp).matches() && !IPV6_PATTERN.matcher(trimmedIp).matches()) {
            throw ConfigurationException.invalidFormat(paramName, ip, "valid IPv4 or IPv6 address");
        }
    }
    
    /**
     * 验证IP白名单
     */
    public static void validateIpWhitelist(String ipWhitelist, String paramName) {
        if (!StringUtils.hasText(ipWhitelist)) {
            return; // 允许为空
        }
        
        String[] ips = ipWhitelist.split(",");
        for (int i = 0; i < ips.length; i++) {
            String ip = ips[i].trim();
            if (StringUtils.hasText(ip)) {
                try {
                    validateIpAddress(ip, paramName + "[" + i + "]");
                } catch (ConfigurationException e) {
                    throw ConfigurationException.invalidFormat(
                        paramName, 
                        ipWhitelist, 
                        "comma-separated list of valid IP addresses"
                    );
                }
            }
        }
    }
    
    /**
     * 验证字符串长度
     */
    public static void validateStringLength(String value, String paramName, int minLength, int maxLength) {
        if (!StringUtils.hasText(value)) {
            if (minLength > 0) {
                throw ConfigurationException.missingRequired(paramName);
            }
            return;
        }
        
        int length = value.trim().length();
        if (length < minLength || length > maxLength) {
            throw ConfigurationException.invalidRange(
                paramName, 
                value, 
                minLength + " to " + maxLength + " characters"
            );
        }
    }
    
    /**
     * 验证存储类型
     */
    public static void validateStorageType(String storageType, String paramName) {
        if (!StringUtils.hasText(storageType)) {
            throw ConfigurationException.missingRequired(paramName);
        }
        
        String type = storageType.trim().toLowerCase();
        if (!"redis".equals(type) && !"memory".equals(type)) {
            throw ConfigurationException.invalidParameter(
                paramName, 
                storageType, 
                "must be either 'redis' or 'memory'"
            );
        }
    }
    
    /**
     * 验证限流算法
     */
    public static void validateRateLimitAlgorithm(String algorithm, String paramName) {
        if (!StringUtils.hasText(algorithm)) {
            throw ConfigurationException.missingRequired(paramName);
        }
        
        String alg = algorithm.trim().toUpperCase();
        if (!"SLIDING_WINDOW".equals(alg) && 
            !"FIXED_WINDOW".equals(alg) && 
            !"TOKEN_BUCKET".equals(alg) && 
            !"LEAKY_BUCKET".equals(alg)) {
            throw ConfigurationException.invalidParameter(
                paramName, 
                algorithm, 
                "must be one of: SLIDING_WINDOW, FIXED_WINDOW, TOKEN_BUCKET, LEAKY_BUCKET"
            );
        }
    }
    
    /**
     * 验证维度类型
     */
    public static void validateDimension(String dimension, String paramName) {
        if (!StringUtils.hasText(dimension)) {
            throw ConfigurationException.missingRequired(paramName);
        }
        
        String dim = dimension.trim().toUpperCase();
        if (!"USER".equals(dim) && 
            !"IP".equals(dim) && 
            !"GLOBAL".equals(dim) && 
            !"USER_METHOD".equals(dim) && 
            !"IP_METHOD".equals(dim) && 
            !"GLOBAL_METHOD".equals(dim) &&
            !"SESSION_METHOD".equals(dim) &&
            !"CUSTOM".equals(dim)) {
            throw ConfigurationException.invalidParameter(
                paramName, 
                dimension, 
                "must be one of: USER, IP, GLOBAL, USER_METHOD, IP_METHOD, GLOBAL_METHOD, SESSION_METHOD, CUSTOM"
            );
        }
    }
    
    /**
     * 验证正整数
     */
    public static void validatePositiveInteger(int value, String paramName) {
        if (value <= 0) {
            throw ConfigurationException.invalidParameter(
                paramName, 
                value, 
                "must be a positive integer"
            );
        }
    }
    
    /**
     * 验证正整数范围
     */
    public static void validateIntegerRange(int value, String paramName, int min, int max) {
        if (value < min || value > max) {
            throw ConfigurationException.invalidRange(
                paramName, 
                value, 
                min + " to " + max
            );
        }
    }
    
    /**
     * 验证SpEL表达式格式
     */
    public static void validateSpelExpression(String expression, String paramName) {
        if (!StringUtils.hasText(expression)) {
            throw ConfigurationException.missingRequired(paramName);
        }
        
        String expr = expression.trim();
        if (!expr.startsWith("#{") || !expr.endsWith("}")) {
            throw ConfigurationException.invalidFormat(
                paramName, 
                expression, 
                "SpEL expression starting with #{} and ending with }"
            );
        }
    }
    
    /**
     * 验证时间超时设置（毫秒）
     */
    public static void validateTimeoutMillis(long timeoutMillis, String paramName) {
        if (timeoutMillis < 100 || timeoutMillis > 300000) { // 100ms to 5 minutes
            throw ConfigurationException.invalidRange(
                paramName, 
                timeoutMillis, 
                "100 to 300000 milliseconds"
            );
        }
    }
    
    /**
     * 批量验证配置参数
     */
    public static class ValidationBuilder {
        
        public ValidationBuilder validateInterval(int value, String paramName) {
            ConfigurationValidator.validateInterval(value, paramName);
            return this;
        }
        
        public ValidationBuilder validatePermits(int value, String paramName) {
            ConfigurationValidator.validatePermits(value, paramName);
            return this;
        }
        
        public ValidationBuilder validateIpAddress(String value, String paramName) {
            ConfigurationValidator.validateIpAddress(value, paramName);
            return this;
        }
        
        public ValidationBuilder validateStorageType(String value, String paramName) {
            ConfigurationValidator.validateStorageType(value, paramName);
            return this;
        }
        
        // 可以继续添加其他验证方法...
    }
    
    /**
     * 创建验证构建器
     */
    public static ValidationBuilder builder() {
        return new ValidationBuilder();
    }
}
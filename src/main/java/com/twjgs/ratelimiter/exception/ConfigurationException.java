package com.twjgs.ratelimiter.exception;

/**
 * 配置异常
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
public class ConfigurationException extends ApiProtectionException {
    
    public static final String ERROR_CODE_INVALID_PARAMETER = "CONFIG_INVALID_PARAMETER";
    public static final String ERROR_CODE_MISSING_REQUIRED = "CONFIG_MISSING_REQUIRED";
    public static final String ERROR_CODE_INVALID_RANGE = "CONFIG_INVALID_RANGE";
    public static final String ERROR_CODE_INVALID_FORMAT = "CONFIG_INVALID_FORMAT";
    
    private final String parameterName;
    private final Object parameterValue;
    
    public ConfigurationException(String message, String errorCode, String parameterName, Object parameterValue) {
        super(message, errorCode);
        this.parameterName = parameterName;
        this.parameterValue = parameterValue;
    }
    
    public ConfigurationException(String message, String errorCode, String parameterName, Object parameterValue, Throwable cause) {
        super(message, errorCode, cause);
        this.parameterName = parameterName;
        this.parameterValue = parameterValue;
    }
    
    @Override
    public String getErrorType() {
        return "CONFIGURATION_ERROR";
    }
    
    @Override
    public int getHttpStatus() {
        return 400; // Bad Request
    }
    
    public String getParameterName() {
        return parameterName;
    }
    
    public Object getParameterValue() {
        return parameterValue;
    }
    
    // 便捷的工厂方法
    public static ConfigurationException invalidParameter(String parameterName, Object value, String reason) {
        String message = String.format("Invalid parameter '%s' with value '%s': %s", parameterName, value, reason);
        return new ConfigurationException(message, ERROR_CODE_INVALID_PARAMETER, parameterName, value);
    }
    
    public static ConfigurationException missingRequired(String parameterName) {
        String message = String.format("Required parameter '%s' is missing", parameterName);
        return new ConfigurationException(message, ERROR_CODE_MISSING_REQUIRED, parameterName, null);
    }
    
    public static ConfigurationException invalidRange(String parameterName, Object value, String validRange) {
        String message = String.format("Parameter '%s' with value '%s' is out of valid range: %s", parameterName, value, validRange);
        return new ConfigurationException(message, ERROR_CODE_INVALID_RANGE, parameterName, value);
    }
    
    public static ConfigurationException invalidFormat(String parameterName, Object value, String expectedFormat) {
        String message = String.format("Parameter '%s' with value '%s' has invalid format, expected: %s", parameterName, value, expectedFormat);
        return new ConfigurationException(message, ERROR_CODE_INVALID_FORMAT, parameterName, value);
    }
}
package io.github.rateLimiter.exception;

/**
 * Exception thrown when there's a configuration error in the rate limiter
 * 
 * <p>This exception is typically thrown during application startup when
 * invalid configurations are detected, such as conflicting GLOBAL rate limits
 * or invalid parameter combinations.
 * 
 * @author Smart Rate Limiter Team
 * @since 1.0.0
 */
public class RateLimiterConfigurationException extends RuntimeException {

    private final String configurationKey;
    private final String suggestedSolution;

    /**
     * Creates a new RateLimiterConfigurationException
     * 
     * @param message the error message
     */
    public RateLimiterConfigurationException(String message) {
        super(message);
        this.configurationKey = null;
        this.suggestedSolution = null;
    }

    /**
     * Creates a new RateLimiterConfigurationException with cause
     * 
     * @param message the error message
     * @param cause the underlying cause
     */
    public RateLimiterConfigurationException(String message, Throwable cause) {
        super(message, cause);
        this.configurationKey = null;
        this.suggestedSolution = null;
    }

    /**
     * Creates a new RateLimiterConfigurationException with detailed information
     * 
     * @param message the error message
     * @param configurationKey the configuration key that caused the error
     * @param suggestedSolution suggested solution to fix the problem
     */
    public RateLimiterConfigurationException(String message, 
                                           String configurationKey, 
                                           String suggestedSolution) {
        super(message);
        this.configurationKey = configurationKey;
        this.suggestedSolution = suggestedSolution;
    }

    /**
     * Creates a new RateLimiterConfigurationException with detailed information and cause
     * 
     * @param message the error message
     * @param cause the underlying cause
     * @param configurationKey the configuration key that caused the error
     * @param suggestedSolution suggested solution to fix the problem
     */
    public RateLimiterConfigurationException(String message, 
                                           Throwable cause,
                                           String configurationKey, 
                                           String suggestedSolution) {
        super(message, cause);
        this.configurationKey = configurationKey;
        this.suggestedSolution = suggestedSolution;
    }

    /**
     * Gets the configuration key that caused the error
     * 
     * @return the configuration key, or null if not specified
     */
    public String getConfigurationKey() {
        return configurationKey;
    }

    /**
     * Gets the suggested solution to fix the configuration error
     * 
     * @return the suggested solution, or null if not specified
     */
    public String getSuggestedSolution() {
        return suggestedSolution;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RateLimiterConfigurationException: ").append(getMessage());
        
        if (configurationKey != null) {
            sb.append(" [configKey=").append(configurationKey).append("]");
        }
        
        if (suggestedSolution != null) {
            sb.append(" [suggestion=").append(suggestedSolution).append("]");
        }
        
        return sb.toString();
    }
}
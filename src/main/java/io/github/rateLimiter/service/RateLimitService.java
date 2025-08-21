package io.github.rateLimiter.service;

import io.github.rateLimiter.model.RateLimitContext;
import io.github.rateLimiter.model.RateLimitResult;

/**
 * Core rate limiting service interface
 * 
 * <p>Defines the contract for rate limiting operations across different
 * storage backends and algorithms.
 * 
 * @author Smart Rate Limiter Team
 * @since 1.0.0
 */
public interface RateLimitService {

    /**
     * Checks if a request is allowed under the specified rate limit
     * 
     * @param context the rate limit context containing all necessary information
     * @return the result of the rate limit check
     */
    RateLimitResult checkRateLimit(RateLimitContext context);

    /**
     * Resets the rate limit for a specific key
     * 
     * @param key the rate limit key to reset
     * @return true if the reset was successful
     */
    boolean resetRateLimit(String key);

    /**
     * Gets the current status of a rate limit without consuming permits
     * 
     * @param context the rate limit context
     * @return the current rate limit status
     */
    RateLimitResult getRateLimitStatus(RateLimitContext context);

    /**
     * Checks if the rate limiting service is healthy and operational
     * 
     * @return true if the service is healthy
     */
    boolean isHealthy();

    /**
     * Gets the type of storage backend used by this service
     * 
     * @return the storage type name
     */
    String getStorageType();
}
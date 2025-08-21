package io.github.rateLimiter.service;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Interface for resolving client IP addresses from HTTP requests
 * 
 * <p>Implementations can handle various proxy scenarios and header configurations
 * to accurately determine the real client IP address.
 * 
 * @author Smart Rate Limiter Team
 * @since 1.0.0
 */
public interface IpResolver {

    /**
     * Resolves the client IP address from the given HTTP request
     * 
     * @param request the HTTP request
     * @return the client IP address
     */
    String resolveIp(HttpServletRequest request);

    /**
     * Checks if this resolver can handle the given request
     * 
     * @param request the HTTP request
     * @return true if this resolver can extract IP from the request
     */
    default boolean canResolve(HttpServletRequest request) {
        return true;
    }

    /**
     * Gets the priority of this resolver when multiple resolvers are available
     * 
     * @return priority value (higher means higher priority)
     */
    default int getPriority() {
        return 0;
    }
}
package io.github.rateLimiter.service;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Interface for resolving user IDs from HTTP requests
 * 
 * <p>Implementations can extract user IDs from various sources such as:
 * <ul>
 *   <li>Security context (Spring Security)</li>
 *   <li>JWT tokens</li>
 *   <li>Session attributes</li>
 *   <li>Request headers</li>
 *   <li>Custom authentication mechanisms</li>
 * </ul>
 * 
 * @author Smart Rate Limiter Team
 * @since 1.0.0
 */
public interface UserIdResolver {

    /**
     * Resolves the user ID from the given HTTP request
     * 
     * @param request the HTTP request
     * @return the user ID, or null if no user is identified
     */
    String resolveUserId(HttpServletRequest request);

    /**
     * Checks if this resolver can handle the given request
     * 
     * @param request the HTTP request
     * @return true if this resolver can extract user ID from the request
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
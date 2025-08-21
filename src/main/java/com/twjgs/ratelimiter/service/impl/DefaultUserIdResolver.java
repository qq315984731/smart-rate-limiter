package com.twjgs.ratelimiter.service.impl;

import com.twjgs.ratelimiter.service.UserIdResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of UserIdResolver
 * 
 * <p>Attempts to resolve user ID from multiple sources in order of preference:
 * <ol>
 *   <li>Spring Security authentication principal (if available)</li>
 *   <li>Custom user ID header</li>
 *   <li>Session attribute</li>
 *   <li>JWT token (if available)</li>
 * </ol>
 * 
 * @author Smart Rate Limiter Team
 * @since 1.0.0
 */
public class DefaultUserIdResolver implements UserIdResolver {

    private static final Logger log = LoggerFactory.getLogger(DefaultUserIdResolver.class);

    private static final String USER_ID_HEADER = "X-User-ID";
    private static final String USER_ID_SESSION_ATTR = "userId";

    @Override
    public String resolveUserId(HttpServletRequest request) {
        // Try Spring Security context first
        String userIdFromSecurity = getUserIdFromSecurityContext();
        if (userIdFromSecurity != null) {
            return userIdFromSecurity;
        }

        // Try custom header
        String userIdFromHeader = request.getHeader(USER_ID_HEADER);
        if (userIdFromHeader != null && !userIdFromHeader.trim().isEmpty()) {
            return userIdFromHeader.trim();
        }

        // Try session attribute
        Object userIdFromSession = request.getSession(false) != null ? 
                request.getSession(false).getAttribute(USER_ID_SESSION_ATTR) : null;
        if (userIdFromSession != null) {
            return userIdFromSession.toString();
        }

        // Try JWT token from Authorization header
        String userIdFromJwt = getUserIdFromJwtToken(request);
        if (userIdFromJwt != null) {
            return userIdFromJwt;
        }

        return null; // No user ID found
    }

    /**
     * Extracts user ID from Spring Security context (if available)
     * Uses reflection to avoid hard dependency on Spring Security
     */
    private String getUserIdFromSecurityContext() {
        try {
            // Use reflection to check if Spring Security is available
            Class<?> securityContextHolderClass = Class.forName("org.springframework.security.core.context.SecurityContextHolder");
            Class<?> securityContextClass = Class.forName("org.springframework.security.core.context.SecurityContext");
            Class<?> authenticationClass = Class.forName("org.springframework.security.core.Authentication");
            
            // Get SecurityContextHolder.getContext()
            var getContextMethod = securityContextHolderClass.getMethod("getContext");
            Object securityContext = getContextMethod.invoke(null);
            
            if (securityContext != null) {
                // Get Authentication from SecurityContext
                var getAuthenticationMethod = securityContextClass.getMethod("getAuthentication");
                Object authentication = getAuthenticationMethod.invoke(securityContext);
                
                if (authentication != null) {
                    // Check if authenticated
                    var isAuthenticatedMethod = authenticationClass.getMethod("isAuthenticated");
                    Boolean isAuthenticated = (Boolean) isAuthenticatedMethod.invoke(authentication);
                    
                    if (Boolean.TRUE.equals(isAuthenticated)) {
                        // Get principal
                        var getPrincipalMethod = authenticationClass.getMethod("getPrincipal");
                        Object principal = getPrincipalMethod.invoke(authentication);
                        
                        if (principal instanceof String) {
                            return (String) principal;
                        } else if (principal != null) {
                            // Try to extract ID from custom principal objects
                            return extractUserIdFromPrincipal(principal);
                        }
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            log.debug("Spring Security not found on classpath, skipping security context resolution");
        } catch (Exception e) {
            log.debug("Failed to get user ID from security context: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Extracts user ID from custom principal objects using reflection
     */
    private String extractUserIdFromPrincipal(Object principal) {
        try {
            // Try common method names
            String[] methodNames = {"getId", "getUserId", "getUsername", "getName"};
            
            for (String methodName : methodNames) {
                try {
                    var method = principal.getClass().getMethod(methodName);
                    Object result = method.invoke(principal);
                    if (result != null) {
                        return result.toString();
                    }
                } catch (Exception e) {
                    // Try next method
                }
            }
            
            // Fallback to toString
            return principal.toString();
            
        } catch (Exception e) {
            log.debug("Failed to extract user ID from principal: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts user ID from JWT token in Authorization header
     */
    private String getUserIdFromJwtToken(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                return parseUserIdFromJwtToken(token);
            }
        } catch (Exception e) {
            log.debug("Failed to extract user ID from JWT token: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Parses user ID from JWT token payload
     * 
     * <p>This is a basic implementation that decodes the JWT payload.
     * For production use, consider using a proper JWT library with signature verification.
     */
    private String parseUserIdFromJwtToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            // Decode payload (middle part)
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            
            // Simple JSON parsing for common user ID fields
            // Note: In production, use a proper JSON library
            String[] userIdFields = {"\"sub\":", "\"userId\":", "\"user_id\":", "\"id\":"};
            
            for (String field : userIdFields) {
                int startIndex = payload.indexOf(field);
                if (startIndex != -1) {
                    startIndex += field.length();
                    int endIndex = payload.indexOf(",", startIndex);
                    if (endIndex == -1) {
                        endIndex = payload.indexOf("}", startIndex);
                    }
                    if (endIndex != -1) {
                        String value = payload.substring(startIndex, endIndex).trim();
                        // Remove quotes if present
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        return value;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse JWT token: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public boolean canResolve(HttpServletRequest request) {
        // This resolver can handle any request
        return true;
    }

    @Override
    public int getPriority() {
        return 0; // Default priority
    }
}
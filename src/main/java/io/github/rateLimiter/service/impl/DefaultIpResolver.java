package io.github.rateLimiter.service.impl;

import io.github.rateLimiter.service.IpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * Default implementation of IpResolver
 * 
 * <p>Resolves client IP address considering various proxy scenarios
 * and forwarded headers. Checks headers in order of reliability.
 * 
 * @author Smart Rate Limiter Team
 * @since 1.0.0
 */
public class DefaultIpResolver implements IpResolver {

    private static final String[] IP_HEADERS = {
        "X-Forwarded-For",
        "X-Real-IP", 
        "X-Original-Forwarded-For",
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP",
        "HTTP_X_FORWARDED_FOR",
        "HTTP_X_FORWARDED",
        "HTTP_X_CLUSTER_CLIENT_IP",
        "HTTP_CLIENT_IP",
        "HTTP_FORWARDED_FOR",
        "HTTP_FORWARDED",
        "HTTP_VIA",
        "REMOTE_ADDR"
    };

    @Override
    public String resolveIp(HttpServletRequest request) {
        // Check proxy headers first
        for (String header : IP_HEADERS) {
            String ip = extractIpFromHeader(request, header);
            if (isValidIp(ip)) {
                return ip;
            }
        }

        // Fallback to remote address
        String remoteAddr = request.getRemoteAddr();
        
        // Handle IPv6 localhost
        if ("0:0:0:0:0:0:0:1".equals(remoteAddr)) {
            return "127.0.0.1";
        }

        return remoteAddr != null ? remoteAddr : "unknown";
    }

    /**
     * Extracts IP address from a specific header
     */
    private String extractIpFromHeader(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (!StringUtils.hasText(value) || "unknown".equalsIgnoreCase(value)) {
            return null;
        }

        // Handle comma-separated IPs (common in X-Forwarded-For)
        if (value.contains(",")) {
            String[] ips = value.split(",");
            for (String ip : ips) {
                ip = ip.trim();
                if (isValidIp(ip)) {
                    return ip;
                }
            }
        }

        return value.trim();
    }

    /**
     * Validates if an IP address is valid and not a private/reserved address
     */
    private boolean isValidIp(String ip) {
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            return false;
        }

        ip = ip.trim();

        // Basic format validation
        if (!isValidIpFormat(ip)) {
            return false;
        }

        // Skip localhost and private networks for proxy scenarios
        // In most cases, we want the real client IP, not internal network IPs
        if (isLocalhost(ip) || isPrivateNetwork(ip)) {
            return false;
        }

        return true;
    }

    /**
     * Validates IP address format (IPv4 and basic IPv6)
     */
    private boolean isValidIpFormat(String ip) {
        // IPv4 validation
        if (ip.matches("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")) {
            return true;
        }

        // Basic IPv6 validation (simplified)
        if (ip.contains(":") && ip.length() <= 39) {
            return true;
        }

        return false;
    }

    /**
     * Checks if IP is localhost
     */
    private boolean isLocalhost(String ip) {
        return "127.0.0.1".equals(ip) || 
               "::1".equals(ip) || 
               "0:0:0:0:0:0:0:1".equals(ip) ||
               "localhost".equalsIgnoreCase(ip);
    }

    /**
     * Checks if IP is in private network ranges
     */
    private boolean isPrivateNetwork(String ip) {
        if (!ip.contains(".")) {
            return false; // Skip IPv6 for simplicity
        }

        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        try {
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);

            // 10.0.0.0/8
            if (first == 10) {
                return true;
            }

            // 172.16.0.0/12
            if (first == 172 && second >= 16 && second <= 31) {
                return true;
            }

            // 192.168.0.0/16
            if (first == 192 && second == 168) {
                return true;
            }

            // 169.254.0.0/16 (link-local)
            if (first == 169 && second == 254) {
                return true;
            }

        } catch (NumberFormatException e) {
            return false;
        }

        return false;
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
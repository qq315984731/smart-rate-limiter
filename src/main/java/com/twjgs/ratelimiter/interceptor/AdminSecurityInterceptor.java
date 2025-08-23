package com.twjgs.ratelimiter.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twjgs.ratelimiter.config.RateLimiterAdminProperties;
import com.twjgs.ratelimiter.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理面板安全拦截器
 * 对管理面板的API请求进行安全验证
 * 
 * @author RateLimiter Team
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "smart.rate-limiter.admin.enabled", havingValue = "true", matchIfMissing = false)
public class AdminSecurityInterceptor implements HandlerInterceptor {

    private final RateLimiterAdminProperties adminProperties;
    private final ObjectMapper objectMapper;

    /**
     * 需要进行安全验证的API路径模式
     */
    private static final String[] SECURED_API_PATTERNS = {
        "/api/config/save",
        "/api/config/delete", 
        "/api/endpoints",
        "/api/config/",
        "/api/refresh"
    };
    
    /**
     * 不需要安全验证的API路径（即使启用了安全检查）
     */
    private static final String[] EXCLUDED_API_PATTERNS = {
        "/api/security-config"  // 安全配置端点本身不需要额外验证
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        
        String requestUri = request.getRequestURI();
        
        // 只对API端点进行验证，排除页面和登录登出
        if (!isSecuredApiPath(requestUri)) {
            return true;
        }

        // 如果未启用头部检查，跳过验证
        if (!adminProperties.getSecurity().isEnableHeaderCheck()) {
            return true;
        }

        // 执行安全验证
        if (!validateSecurityHeader(request) || !validateIpWhitelist(request)) {
            handleSecurityFailure(response, request);
            return false;
        }

        return true;
    }

    /**
     * 检查是否为需要安全验证的API路径
     */
    private boolean isSecuredApiPath(String requestUri) {
        if (!StringUtils.hasText(requestUri)) {
            return false;
        }

        // 提取相对于管理面板基础路径的URI
        String basePath = adminProperties.getBasePath();
        if (requestUri.startsWith(basePath)) {
            String relativePath = requestUri.substring(basePath.length());
            
            // 首先检查是否在排除列表中
            boolean isExcluded = Arrays.stream(EXCLUDED_API_PATTERNS)
                    .anyMatch(pattern -> {
                        if (pattern.endsWith("/")) {
                            return relativePath.startsWith(pattern);
                        }
                        return relativePath.equals(pattern) || relativePath.startsWith(pattern + "/");
                    });
            
            if (isExcluded) {
                return false;
            }
            
            // 检查是否匹配需要保护的API模式
            return Arrays.stream(SECURED_API_PATTERNS)
                    .anyMatch(pattern -> {
                        if (pattern.endsWith("/")) {
                            return relativePath.startsWith(pattern);
                        }
                        return relativePath.equals(pattern) || relativePath.startsWith(pattern + "/");
                    });
        }
        
        return false;
    }

    /**
     * 验证安全请求头
     */
    private boolean validateSecurityHeader(HttpServletRequest request) {
        String headerName = adminProperties.getSecurity().getHeaderName();
        String expectedValue = adminProperties.getSecurity().getHeaderValue();
        
        if (!StringUtils.hasText(headerName) || !StringUtils.hasText(expectedValue)) {
            log.warn("Security header validation enabled but headerName or headerValue not configured");
            return false;
        }

        String actualValue = request.getHeader(headerName);
        if (!expectedValue.equals(actualValue)) {
            log.warn("Security header validation failed. Expected header '{}' with value '{}', but got '{}'", 
                    headerName, expectedValue, actualValue);
            return false;
        }

        return true;
    }

    /**
     * 验证IP白名单
     */
    private boolean validateIpWhitelist(HttpServletRequest request) {
        String allowedIps = adminProperties.getSecurity().getAllowedIps();
        String clientIp = IpUtils.getClientIp(request);
        
        boolean isValid = IpUtils.validateIpWhitelist(clientIp, allowedIps);
        if (!isValid) {
            log.warn("IP whitelist validation failed for IP: {}, allowed IPs: {}", clientIp, allowedIps);
        }
        
        return isValid;
    }


    /**
     * 处理安全验证失败
     */
    private void handleSecurityFailure(HttpServletResponse response, HttpServletRequest request) throws IOException {
        String clientIp = IpUtils.getClientIp(request);
        log.error("Security validation failed for request: {} from IP: {}", request.getRequestURI(), clientIp);
        
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", "SECURITY_VALIDATION_FAILED");
        errorResponse.put("message", "安全验证失败，访问被拒绝");
        errorResponse.put("timestamp", System.currentTimeMillis());
        
        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
}
package com.twjgs.ratelimiter.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * IP地址获取工具类
 * 统一处理客户端真实IP获取逻辑，避免代码重复
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
public class IpUtils {
    
    private static final String UNKNOWN = "unknown";
    private static final String DEFAULT_IP = "0.0.0.0";
    
    // 常见的代理头信息，按优先级排序
    private static final String[] IP_HEADERS = {
        "X-Forwarded-For",
        "X-Real-IP", 
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP",
        "HTTP_CLIENT_IP",
        "HTTP_X_FORWARDED_FOR",
        "X-Original-Forwarded-For"
    };
    
    /**
     * 获取客户端真实IP地址
     * 
     * @param request HTTP请求对象
     * @return 客户端IP地址，如果获取失败返回默认IP
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return DEFAULT_IP;
        }
        
        String ip = null;
        
        // 按优先级检查各种代理头
        for (String header : IP_HEADERS) {
            ip = request.getHeader(header);
            if (isValidIp(ip)) {
                // 如果是多个IP（如X-Forwarded-For: IP1, IP2, IP3），取第一个
                return extractFirstIp(ip);
            }
        }
        
        // 都没有获取到，使用remoteAddr
        ip = request.getRemoteAddr();
        return StringUtils.hasText(ip) ? ip.trim() : DEFAULT_IP;
    }
    
    /**
     * 检查IP是否有效
     */
    private static boolean isValidIp(String ip) {
        return StringUtils.hasText(ip) && 
               !UNKNOWN.equalsIgnoreCase(ip.trim()) &&
               !"null".equalsIgnoreCase(ip.trim());
    }
    
    /**
     * 从可能包含多个IP的字符串中提取第一个IP
     */
    private static String extractFirstIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return DEFAULT_IP;
        }
        
        String trimmedIp = ip.trim();
        int commaIndex = trimmedIp.indexOf(',');
        
        if (commaIndex != -1) {
            return trimmedIp.substring(0, commaIndex).trim();
        }
        
        return trimmedIp;
    }
    
    /**
     * 检查IP是否匹配指定的模式（支持通配符）
     * 
     * @param clientIp 客户端IP
     * @param allowedPattern 允许的IP模式，支持 *, 192.168.*, ::1 等
     * @return 是否匹配
     */
    public static boolean isIpMatched(String clientIp, String allowedPattern) {
        if (!StringUtils.hasText(clientIp) || !StringUtils.hasText(allowedPattern)) {
            return false;
        }
        
        String pattern = allowedPattern.trim();
        String ip = clientIp.trim();
        
        // 通配符匹配所有IP
        if ("*".equals(pattern)) {
            return true;
        }
        
        // IPv6 localhost的不同表示形式处理
        if ("::1".equals(pattern) && (
            "::1".equals(ip) || 
            "0:0:0:0:0:0:0:1".equals(ip) ||
            "127.0.0.1".equals(ip)
        )) {
            return true;
        }
        
        // localhost处理
        if ("localhost".equalsIgnoreCase(pattern) && (
            "127.0.0.1".equals(ip) ||
            "::1".equals(ip) ||
            "0:0:0:0:0:0:0:1".equals(ip) ||
            "localhost".equalsIgnoreCase(ip)
        )) {
            return true;
        }
        
        // 前缀通配符匹配
        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return ip.startsWith(prefix);
        }
        
        // 精确匹配
        return ip.equals(pattern);
    }
    
    /**
     * 验证IP白名单
     * 
     * @param clientIp 客户端IP
     * @param allowedIps 允许的IP列表，用逗号分隔
     * @return 是否通过白名单验证
     */
    public static boolean validateIpWhitelist(String clientIp, String allowedIps) {
        if (!StringUtils.hasText(allowedIps)) {
            // 没有配置IP白名单，默认允许
            return true;
        }
        
        if (!StringUtils.hasText(clientIp)) {
            return false;
        }
        
        String[] ipList = allowedIps.split(",");
        for (String allowedIp : ipList) {
            if (isIpMatched(clientIp, allowedIp.trim())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 判断是否为内网IP
     */
    public static boolean isInternalIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        
        String trimmedIp = ip.trim();
        
        // IPv6 loopback
        if ("::1".equals(trimmedIp) || "0:0:0:0:0:0:0:1".equals(trimmedIp)) {
            return true;
        }
        
        // IPv4 internal ranges
        return trimmedIp.startsWith("127.") ||          // 127.0.0.0/8 (loopback)
               trimmedIp.startsWith("10.") ||           // 10.0.0.0/8 (private)
               trimmedIp.startsWith("192.168.") ||      // 192.168.0.0/16 (private)
               (trimmedIp.startsWith("172.") &&         // 172.16.0.0/12 (private)
                isInRange172(trimmedIp)) ||
               trimmedIp.startsWith("169.254.") ||      // 169.254.0.0/16 (link-local)
               "localhost".equalsIgnoreCase(trimmedIp);
    }
    
    /**
     * 检查是否在172.16.0.0/12范围内
     */
    private static boolean isInRange172(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                int secondOctet = Integer.parseInt(parts[1]);
                return secondOctet >= 16 && secondOctet <= 31;
            }
        } catch (NumberFormatException e) {
            // 解析失败，返回false
        }
        return false;
    }
}
package io.github.rateLimiter.controller;

import io.github.rateLimiter.config.RateLimiterAdminProperties;
import io.github.rateLimiter.model.DynamicRateLimitConfig;
import io.github.rateLimiter.model.EndpointInfo;
import io.github.rateLimiter.service.DynamicConfigService;
import io.github.rateLimiter.service.EndpointDiscoveryService;
import io.github.rateLimiter.service.FileLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 限流管理控制器
 * 提供Web界面和API接口
 * 
 * @author RateLimiter Team
 * @since 1.0.0
 */
@Controller
@RequestMapping("${rate-limiter.admin.base-path:/admin/rate-limiter}")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "rate-limiter.admin.enabled", havingValue = "true", matchIfMissing = false)
public class RateLimiterManagementController {
    
    private final DynamicConfigService dynamicConfigService;
    private final EndpointDiscoveryService endpointDiscoveryService;
    private final FileLogService fileLogService;
    private final RateLimiterAdminProperties adminProperties;
    
    private static final String SESSION_USER_KEY = "rate_limiter_admin_user";
    
    /**
     * 登录页面
     */
    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("basePath", adminProperties.getBasePath());
        return "rate-limiter/login";
    }
    
    /**
     * 登录处理
     */
    @PostMapping("/login")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> login(
            @RequestParam String username,
            @RequestParam String password,
            HttpSession session,
            HttpServletRequest request) {
        
        Map<String, Object> result = new HashMap<>();
        String clientIp = getClientIp(request);
        
        if (adminProperties.getUsername().equals(username) && 
            adminProperties.getPassword().equals(password)) {
            
            session.setAttribute(SESSION_USER_KEY, username);
            session.setMaxInactiveInterval(adminProperties.getSessionTimeout() * 60);
            
            result.put("success", true);
            result.put("message", "登录成功");
            
            log.info("Rate limiter admin login success: user={}, IP={}", username, clientIp);
            
        } else {
            result.put("success", false);
            result.put("message", "用户名或密码错误");
            
            log.warn("Rate limiter admin login failed: user={}, IP={}", username, clientIp);
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 退出登录
     */
    @PostMapping("/logout")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> logout(HttpSession session, HttpServletRequest request) {
        String username = (String) session.getAttribute(SESSION_USER_KEY);
        String clientIp = getClientIp(request);
        
        session.removeAttribute(SESSION_USER_KEY);
        session.invalidate();
        
        // 记录登出日志
        if (username != null) {
            log.info("Rate limiter admin logout: user={}, IP={}", username, clientIp);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "退出成功");
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 主管理页面
     */
    @GetMapping("/dashboard")
    public String dashboard(Model model, HttpSession session) {
        if (!isLoggedIn(session)) {
            return "redirect:" + adminProperties.getBasePath() + "/login";
        }
        
        // 获取接口统计信息
        Map<String, Object> statistics = endpointDiscoveryService.getStatistics();
        model.addAttribute("statistics", statistics);
        
        // 移除操作日志功能
        
        model.addAttribute("basePath", adminProperties.getBasePath());
        model.addAttribute("currentUser", session.getAttribute(SESSION_USER_KEY));
        
        return "rate-limiter/dashboard";
    }
    
    /**
     * 接口配置页面
     */
    @GetMapping("/endpoints")
    public String endpointsPage(Model model, HttpSession session,
                               @RequestParam(required = false) String search,
                               @RequestParam(required = false) String configType) {
        if (!isLoggedIn(session)) {
            return "redirect:" + adminProperties.getBasePath() + "/login";
        }
        
        List<EndpointInfo> endpoints;
        
        if (search != null && !search.trim().isEmpty()) {
            endpoints = endpointDiscoveryService.searchEndpoints(search);
        } else {
            endpoints = endpointDiscoveryService.discoverAllEndpoints();
        }
        
        // 按配置类型过滤
        if (configType != null && !configType.isEmpty() && !"ALL".equals(configType)) {
            endpoints = endpoints.stream()
                .filter(endpoint -> configType.equals(endpoint.getEffectiveConfigType()))
                .toList();
        }
        
        model.addAttribute("endpoints", endpoints);
        model.addAttribute("search", search);
        model.addAttribute("configType", configType);
        model.addAttribute("basePath", adminProperties.getBasePath());
        model.addAttribute("currentUser", session.getAttribute(SESSION_USER_KEY));
        
        return "rate-limiter/endpoints";
    }
    
    /**
     * 配置编辑页面
     */
    @GetMapping("/config/{methodSignature}")
    public String configPage(@PathVariable String methodSignature, Model model, HttpSession session) {
        if (!isLoggedIn(session)) {
            return "redirect:" + adminProperties.getBasePath() + "/login";
        }
        
        // 获取接口信息
        EndpointInfo endpointInfo = endpointDiscoveryService.getEndpointByMethodSignature(methodSignature);
        if (endpointInfo == null) {
            model.addAttribute("error", "接口不存在");
            return "rate-limiter/error";
        }
        
        // 获取当前动态配置
        DynamicRateLimitConfig currentConfig = dynamicConfigService.getDynamicConfig(methodSignature);
        
        model.addAttribute("endpoint", endpointInfo);
        model.addAttribute("currentConfig", currentConfig);
        model.addAttribute("methodSignature", methodSignature);
        model.addAttribute("basePath", adminProperties.getBasePath());
        model.addAttribute("currentUser", session.getAttribute(SESSION_USER_KEY));
        
        return "rate-limiter/config";
    }
    
    /**
     * 保存配置
     */
    @PostMapping("/api/config/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveConfig(
            @RequestBody Map<String, Object> configData,
            HttpSession session) {
        
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "未登录"));
        }
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String methodSignature = (String) configData.get("methodSignature");
            String operator = (String) session.getAttribute(SESSION_USER_KEY);
            
            // 获取变更前的配置
            DynamicRateLimitConfig beforeConfig = dynamicConfigService.getDynamicConfig(methodSignature);
            
            // 构建配置对象
            DynamicRateLimitConfig config = buildConfigFromData(configData);
            config.setSource("DYNAMIC_WEB");
            
            // 保存配置
            dynamicConfigService.saveDynamicConfig(methodSignature, config, operator);
            
            // 记录配置变更日志
            fileLogService.logConfigChange(methodSignature, beforeConfig, config, operator);
            
            result.put("success", true);
            result.put("message", "配置保存成功");
            
            log.info("Rate limiter config saved: method={}, operator={}", methodSignature, operator);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "配置保存失败: " + e.getMessage());
            log.error("Failed to save rate limiter config", e);
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 删除配置
     */
    @PostMapping("/api/config/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteConfig(
            @RequestParam String methodSignature,
            HttpSession session) {
        
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "未登录"));
        }
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String operator = (String) session.getAttribute(SESSION_USER_KEY);
            
            // 获取删除前的配置
            DynamicRateLimitConfig beforeConfig = dynamicConfigService.getDynamicConfig(methodSignature);
            
            // 删除配置
            dynamicConfigService.deleteDynamicConfig(methodSignature, operator);
            
            // 记录配置变更日志（删除后配置为null）
            if (beforeConfig != null) {
                fileLogService.logConfigChange(methodSignature, beforeConfig, null, operator);
            }
            
            result.put("success", true);
            result.put("message", "配置删除成功");
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "配置删除失败: " + e.getMessage());
            log.error("Failed to delete rate limiter config", e);
        }
        
        return ResponseEntity.ok(result);
    }
    
    
    /**
     * 获取接口列表API
     */
    @GetMapping("/api/endpoints")
    @ResponseBody
    public ResponseEntity<List<EndpointInfo>> getEndpoints(
            @RequestParam(required = false) String search,
            HttpSession session) {
        
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(401).build();
        }
        
        List<EndpointInfo> endpoints;
        if (search != null && !search.trim().isEmpty()) {
            endpoints = endpointDiscoveryService.searchEndpoints(search);
        } else {
            endpoints = endpointDiscoveryService.discoverAllEndpoints();
        }
        
        return ResponseEntity.ok(endpoints);
    }
    
    /**
     * 获取配置API
     */
    @GetMapping("/api/config/{methodSignature}")
    @ResponseBody
    public ResponseEntity<DynamicRateLimitConfig> getConfig(
            @PathVariable String methodSignature,
            HttpSession session) {
        
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(401).build();
        }
        
        DynamicRateLimitConfig config = dynamicConfigService.getDynamicConfig(methodSignature);
        return ResponseEntity.ok(config);
    }
    
    /**
     * 刷新接口发现缓存
     */
    @PostMapping("/api/refresh")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> refresh(HttpSession session) {
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "未登录"));
        }
        
        try {
            endpointDiscoveryService.refreshCache();
            return ResponseEntity.ok(Map.of("success", true, "message", "缓存刷新成功"));
        } catch (Exception e) {
            log.error("Failed to refresh cache", e);
            return ResponseEntity.ok(Map.of("success", false, "message", "缓存刷新失败: " + e.getMessage()));
        }
    }
    
    /**
     * 检查是否已登录
     */
    private boolean isLoggedIn(HttpSession session) {
        return session.getAttribute(SESSION_USER_KEY) != null;
    }
    
    /**
     * 获取客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * 从请求数据构建配置对象
     */
    private DynamicRateLimitConfig buildConfigFromData(Map<String, Object> data) {
        DynamicRateLimitConfig config = new DynamicRateLimitConfig();
        
        config.setType(DynamicRateLimitConfig.ConfigType.SINGLE_RATE_LIMIT);
        config.setEnabled((Boolean) data.getOrDefault("enabled", true));
        config.setDimension((String) data.get("dimension"));
        
        // 安全的数字转换
        if (data.get("permits") != null) {
            config.setPermits(convertToInteger(data.get("permits")));
        }
        
        if (data.get("window") != null) {
            config.setWindow(convertToInteger(data.get("window")));
        }
        
        config.setAlgorithm((String) data.getOrDefault("algorithm", "SLIDING_WINDOW"));
        
        // 高级配置
        if (data.containsKey("bucketCapacity") && data.get("bucketCapacity") != null) {
            config.setBucketCapacity(convertToInteger(data.get("bucketCapacity")));
        }
        if (data.containsKey("refillRate") && data.get("refillRate") != null) {
            config.setRefillRate(convertToDouble(data.get("refillRate")));
        }
        if (data.containsKey("strategy")) {
            config.setStrategy((String) data.get("strategy"));
        }
        if (data.containsKey("queueTimeout") && data.get("queueTimeout") != null) {
            config.setQueueTimeout(convertToLong(data.get("queueTimeout")));
        }
        if (data.containsKey("message")) {
            config.setMessage((String) data.get("message"));
        }
        
        // 元数据
        config.setReason((String) data.get("reason"));
        config.setTemporary((Boolean) data.getOrDefault("temporary", false));
        
        if (Boolean.TRUE.equals(config.getTemporary()) && data.containsKey("expireHours")) {
            int expireHours = convertToInteger(data.get("expireHours"));
            config.setExpireTime(LocalDateTime.now().plusHours(expireHours));
        }
        
        return config;
    }
    
    private Integer convertToInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            return Integer.parseInt((String) value);
        }
        return null;
    }
    
    private Double convertToDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            return Double.parseDouble((String) value);
        }
        return null;
    }
    
    private Long convertToLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value instanceof String) {
            return Long.parseLong((String) value);
        }
        return null;
    }
}
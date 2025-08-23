package com.twjgs.ratelimiter.controller;

import com.twjgs.ratelimiter.config.RateLimiterAdminProperties;
import com.twjgs.ratelimiter.model.DynamicRateLimitConfig;
import com.twjgs.ratelimiter.model.EndpointInfo;
import com.twjgs.ratelimiter.model.IdempotentConfig;
import com.twjgs.ratelimiter.model.DuplicateSubmitConfig;
import com.twjgs.ratelimiter.service.DynamicConfigService;
import com.twjgs.ratelimiter.service.EndpointDiscoveryService;
import com.twjgs.ratelimiter.service.FileLogService;
import com.twjgs.ratelimiter.service.IdempotentConfigService;
import com.twjgs.ratelimiter.service.DuplicateSubmitConfigService;
import com.twjgs.ratelimiter.service.StartupCleanupService;
import com.twjgs.ratelimiter.annotation.Idempotent;
import com.twjgs.ratelimiter.annotation.DuplicateSubmit;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping("${smart.rate-limiter.admin.base-path:/admin/rate-limiter}")
@Slf4j
@ConditionalOnProperty(name = "smart.rate-limiter.admin.enabled", havingValue = "true", matchIfMissing = false)
public class RateLimiterManagementController {
    
    private final DynamicConfigService dynamicConfigService;
    private final EndpointDiscoveryService endpointDiscoveryService;
    private final FileLogService fileLogService;
    private final RateLimiterAdminProperties adminProperties;
    private final IdempotentConfigService idempotentConfigService;
    private final DuplicateSubmitConfigService duplicateSubmitConfigService;
    private final StartupCleanupService startupCleanupService;
    
    private static final String SESSION_USER_KEY = "rate_limiter_admin_user";
    
    public RateLimiterManagementController(
            DynamicConfigService dynamicConfigService,
            EndpointDiscoveryService endpointDiscoveryService,
            FileLogService fileLogService,
            RateLimiterAdminProperties adminProperties,
            IdempotentConfigService idempotentConfigService,
            DuplicateSubmitConfigService duplicateSubmitConfigService,
            @Autowired(required = false) StartupCleanupService startupCleanupService) {
        
        this.dynamicConfigService = dynamicConfigService;
        this.endpointDiscoveryService = endpointDiscoveryService;
        this.fileLogService = fileLogService;
        this.adminProperties = adminProperties;
        this.idempotentConfigService = idempotentConfigService;
        this.duplicateSubmitConfigService = duplicateSubmitConfigService;
        this.startupCleanupService = startupCleanupService;
    }
    
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
            @RequestParam(value = "username", required = true) String username,
            @RequestParam(value = "password", required = true) String password,
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
        
        // 添加安全配置信息（仅用于前端判断是否启用安全验证）
        addSecurityConfigToModel(model);
        
        return "rate-limiter/dashboard";
    }
    
    /**
     * 接口配置页面
     */
    @GetMapping("/endpoints")
    public String endpointsPage(Model model, HttpSession session,
                               @RequestParam(value = "search", required = false) String search,
                               @RequestParam(value = "configType", required = false) String configType) {
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
        
        // 添加安全配置信息
        addSecurityConfigToModel(model);
        
        return "rate-limiter/endpoints";
    }
    
    /**
     * 配置编辑页面
     */
    @GetMapping("/config/{methodSignature}")
    public String configPage(@PathVariable(value = "methodSignature") String methodSignature, Model model, HttpSession session) {
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
        
        // 添加安全配置信息
        addSecurityConfigToModel(model);
        
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
            @RequestParam(value = "methodSignature", required = true) String methodSignature,
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
            @RequestParam(value = "search", required = false) String search,
            HttpSession session, HttpServletRequest request) {
        
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(401).build();
        }
        
        String username = (String) session.getAttribute(SESSION_USER_KEY);
        String clientIp = getClientIp(request);
        
        try {
            List<EndpointInfo> endpoints;
            if (search != null && !search.trim().isEmpty()) {
                endpoints = endpointDiscoveryService.searchEndpoints(search);
                log.debug("API接口搜索: user={}, IP={}, search='{}', results={}", 
                    username, clientIp, search, endpoints.size());
            } else {
                endpoints = endpointDiscoveryService.discoverAllEndpoints();
                log.debug("API接口列表获取: user={}, IP={}, total_endpoints={}", 
                    username, clientIp, endpoints.size());
            }
            
            return ResponseEntity.ok(endpoints);
        } catch (Exception e) {
            log.error("获取API接口列表失败: user={}, IP={}, search='{}', error={}", 
                username, clientIp, search, e.getMessage(), e);
            return ResponseEntity.status(500).body(Collections.emptyList());
        }
    }
    
    /**
     * 获取配置API
     */
    @GetMapping("/api/config/{methodSignature}")
    @ResponseBody
    public ResponseEntity<DynamicRateLimitConfig> getConfig(
            @PathVariable(value = "methodSignature") String methodSignature,
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
     * 获取安全配置信息（仅限已登录用户）
     */
    @GetMapping("/api/security-config")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSecurityConfig(HttpSession session) {
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "未登录"));
        }
        
        Map<String, Object> config = new HashMap<>();
        config.put("headerCheckEnabled", adminProperties.getSecurity().isEnableHeaderCheck());
        config.put("headerName", adminProperties.getSecurity().getHeaderName());
        
        // 只有启用了头部检查才返回headerValue
        if (adminProperties.getSecurity().isEnableHeaderCheck()) {
            config.put("headerValue", adminProperties.getSecurity().getHeaderValue());
        }
        
        return ResponseEntity.ok(Map.of("success", true, "data", config));
    }
    
    // ========== API保护功能管理接口 ==========
    
    /**
     * API保护管理页面
     */
    @GetMapping("/api-protection")
    public String apiProtectionPage(Model model, HttpSession session, HttpServletRequest request) {
        if (!isLoggedIn(session)) {
            return "redirect:" + adminProperties.getBasePath() + "/login";
        }
        
        String username = (String) session.getAttribute(SESSION_USER_KEY);
        String clientIp = getClientIp(request);
        
        model.addAttribute("basePath", adminProperties.getBasePath());
        addSecurityConfigToModel(model);
        
        try {
            // 获取所有端点信息（包含API保护状态）
            List<EndpointInfo> endpoints = endpointDiscoveryService.discoverAllEndpoints();
            model.addAttribute("endpoints", endpoints);
            
            // 记录访问日志
            log.info("API保护管理页面访问: user={}, IP={}, endpoints_count={}", 
                username, clientIp, endpoints.size());
                
        } catch (Exception e) {
            log.error("加载API保护页面失败: user={}, IP={}, error={}", 
                username, clientIp, e.getMessage(), e);
            model.addAttribute("endpoints", Collections.emptyList());
            model.addAttribute("message", "加载接口信息失败: " + e.getMessage());
            model.addAttribute("messageType", "error");
        }
        
        return "rate-limiter/api-protection";
    }
    
    /**
     * 获取所有幂等性配置列表
     */
    @GetMapping("/api/idempotent/list")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getIdempotentConfigList(HttpSession session) {
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(401).body(Collections.emptyList());
        }
        
        try {
            List<Map<String, Object>> configs = new ArrayList<>();
            
            // 检查服务是否可用
            if (idempotentConfigService == null || endpointDiscoveryService == null) {
                return ResponseEntity.ok(configs);
            }
            
            // 获取所有接口信息
            List<EndpointInfo> endpoints = endpointDiscoveryService.discoverAllEndpoints();
            
            // 从动态配置服务获取已保存的配置
            List<IdempotentConfig> savedConfigs = idempotentConfigService.getAllConfigs();
            Map<String, IdempotentConfig> savedConfigMap = savedConfigs.stream()
                .collect(java.util.stream.Collectors.toMap(
                    IdempotentConfig::getMethodSignature, 
                    config -> config
                ));
            
            // 处理有注解的接口
            for (EndpointInfo endpoint : endpoints) {
                if (endpoint.isHasIdempotentAnnotation()) {
                    Map<String, Object> configMap = new HashMap<>();
                    configMap.put("methodSignature", endpoint.getMethodSignature());
                    
                    // 优先使用动态配置，否则使用注解配置
                    IdempotentConfig savedConfig = savedConfigMap.get(endpoint.getMethodSignature());
                    if (savedConfig != null) {
                        // 使用动态配置
                        configMap.put("enabled", savedConfig.isEnabled());
                        configMap.put("timeout", savedConfig.getTimeout());
                        configMap.put("keyStrategy", savedConfig.getKeyStrategy());
                        configMap.put("keyExpression", savedConfig.getKeyExpression());
                        configMap.put("returnFirstResult", savedConfig.isReturnFirstResult());
                        configMap.put("message", savedConfig.getMessage());
                        configMap.put("allowRetryOnFailure", savedConfig.isAllowRetryOnFailure());
                        configMap.put("failureDetection", savedConfig.getFailureDetection());
                        configMap.put("failureExceptions", savedConfig.getFailureExceptions());
                        configMap.put("failureCondition", savedConfig.getFailureCondition());
                        configMap.put("configType", "DYNAMIC");
                        configMap.put("operator", savedConfig.getOperator());
                        configMap.put("updateTime", savedConfig.getUpdateTime());
                    } else {
                        // 使用注解配置
                        Idempotent annotation = endpoint.getIdempotentAnnotation();
                        configMap.put("enabled", true);
                        configMap.put("timeout", annotation.timeout());
                        configMap.put("keyStrategy", annotation.keyStrategy().name());
                        configMap.put("keyExpression", annotation.keyExpression());
                        configMap.put("returnFirstResult", annotation.returnFirstResult());
                        configMap.put("message", annotation.message());
                        configMap.put("allowRetryOnFailure", annotation.allowRetryOnFailure());
                        configMap.put("failureDetection", annotation.failureDetection().name());
                        configMap.put("failureExceptions", Arrays.toString(annotation.failureExceptions()));
                        configMap.put("failureCondition", annotation.failureCondition());
                        configMap.put("configType", "ANNOTATION");
                    }
                    
                    configs.add(configMap);
                }
            }
            
            return ResponseEntity.ok(configs);
        } catch (Exception e) {
            log.error("获取幂等性配置列表失败", e);
            return ResponseEntity.ok(Collections.emptyList());
        }
    }
    
    /**
     * 获取幂等性配置
     */
    @GetMapping("/api/idempotent/{methodSignature}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getIdempotentConfig(
            @PathVariable(value = "methodSignature") String methodSignature, HttpSession session) {
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "未登录"));
        }
        
        try {
            if (idempotentConfigService == null) {
                // 返回默认配置
                Map<String, Object> config = new HashMap<>();
                config.put("methodSignature", methodSignature);
                config.put("enabled", false);
                config.put("timeout", 60);  // 默认60秒，匹配简化设计
                config.put("keyStrategy", "USER_PARAMS");
                config.put("keyExpression", "");
                config.put("returnFirstResult", true);
                config.put("message", "请求正在处理中，请勿重复操作");
                config.put("allowRetryOnFailure", true);
                config.put("failureDetection", "ALL");
                config.put("failureExceptions", "");
                config.put("failureCondition", "");
                return ResponseEntity.ok(Map.of("success", true, "data", config));
            }
            
            IdempotentConfig idempotentConfig = idempotentConfigService.getConfig(methodSignature);
            
            if (idempotentConfig != null) {
                Map<String, Object> config = new HashMap<>();
                config.put("methodSignature", idempotentConfig.getMethodSignature());
                config.put("enabled", idempotentConfig.isEnabled());
                config.put("timeout", idempotentConfig.getTimeout());
                config.put("keyStrategy", idempotentConfig.getKeyStrategy());
                config.put("keyExpression", idempotentConfig.getKeyExpression());
                config.put("returnFirstResult", idempotentConfig.isReturnFirstResult());
                config.put("message", idempotentConfig.getMessage());
                config.put("allowRetryOnFailure", idempotentConfig.isAllowRetryOnFailure());
                config.put("failureDetection", idempotentConfig.getFailureDetection());
                config.put("failureExceptions", idempotentConfig.getFailureExceptions());
                config.put("failureCondition", idempotentConfig.getFailureCondition());
                
                return ResponseEntity.ok(Map.of("success", true, "data", config));
            } else {
                // 返回默认配置
                Map<String, Object> config = new HashMap<>();
                config.put("methodSignature", methodSignature);
                config.put("enabled", false);
                config.put("timeout", 60);  // 默认60秒，匹配简化设计
                config.put("keyStrategy", "USER_PARAMS");
                config.put("keyExpression", "");
                config.put("returnFirstResult", true);
                config.put("message", "请求正在处理中，请勿重复操作");
                config.put("allowRetryOnFailure", true);
                config.put("failureDetection", "ALL");
                config.put("failureExceptions", "");
                config.put("failureCondition", "");
                
                return ResponseEntity.ok(Map.of("success", true, "data", config));
            }
        } catch (Exception e) {
            log.error("获取幂等性配置失败", e);
            return ResponseEntity.status(500)
                .body(Map.of("success", false, "message", "获取配置失败: " + e.getMessage()));
        }
    }
    
    /**
     * 保存幂等性配置
     */
    @PostMapping("/api/idempotent/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveIdempotentConfig(
            @RequestBody Map<String, Object> requestData, 
            HttpServletRequest request, HttpSession session) {
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "未登录"));
        }
        
        try {
            String methodSignature = (String) requestData.get("methodSignature");
            Boolean enabled = (Boolean) requestData.get("enabled");
            Integer timeout = convertToInteger(requestData.get("timeout"));
            String keyStrategy = (String) requestData.get("keyStrategy");
            String keyExpression = (String) requestData.get("keyExpression");
            Boolean returnFirstResult = (Boolean) requestData.get("returnFirstResult");
            String message = (String) requestData.get("message");
            Boolean allowRetryOnFailure = (Boolean) requestData.get("allowRetryOnFailure");
            String failureDetection = (String) requestData.get("failureDetection");
            String failureExceptions = (String) requestData.get("failureExceptions");
            String failureCondition = (String) requestData.get("failureCondition");
            
            if (methodSignature == null || methodSignature.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "方法签名不能为空"));
            }
            
            if (idempotentConfigService == null) {
                return ResponseEntity.status(500)
                    .body(Map.of("success", false, "message", "系统未启用幂等性配置功能"));
            }
            
            String operator = (String) session.getAttribute(SESSION_USER_KEY);
            
            // 构建配置对象
            IdempotentConfig config = IdempotentConfig.builder()
                .methodSignature(methodSignature)
                .enabled(enabled != null ? enabled : false)
                .timeout(timeout != null ? timeout : 60)  // 默认60秒，匹配简化设计
                .keyStrategy(keyStrategy != null ? keyStrategy : "USER_PARAMS")
                .keyExpression(keyExpression != null ? keyExpression : "")
                .returnFirstResult(returnFirstResult != null ? returnFirstResult : true)
                .message(message != null ? message : "请求正在处理中，请勿重复操作")
                .allowRetryOnFailure(allowRetryOnFailure != null ? allowRetryOnFailure : true)
                .failureDetection(failureDetection != null ? failureDetection : "ALL")
                .failureExceptions(failureExceptions != null ? failureExceptions : "")
                .failureCondition(failureCondition != null ? failureCondition : "")
                .operator(operator)
                .build();
            
            // 保存配置
            idempotentConfigService.saveConfig(config);
            
            // 记录操作日志
            log.info("Idempotent config saved: method={}, operator={}, ip={}", 
                methodSignature, operator, getClientIp(request));
            
            return ResponseEntity.ok(Map.of("success", true, "message", "幂等性配置保存成功"));
            
        } catch (Exception e) {
            log.error("保存幂等性配置失败", e);
            return ResponseEntity.status(500)
                .body(Map.of("success", false, "message", "保存配置失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取所有防重复提交配置列表
     */
    @GetMapping("/api/duplicate-submit/list")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getDuplicateSubmitConfigList(HttpSession session) {
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(401).body(Collections.emptyList());
        }
        
        try {
            List<Map<String, Object>> configs = new ArrayList<>();
            
            // 检查服务是否可用
            if (duplicateSubmitConfigService == null || endpointDiscoveryService == null) {
                return ResponseEntity.ok(configs);
            }
            
            // 获取所有接口信息
            List<EndpointInfo> endpoints = endpointDiscoveryService.discoverAllEndpoints();
            
            // 从动态配置服务获取已保存的配置
            List<DuplicateSubmitConfig> savedConfigs = duplicateSubmitConfigService.getAllConfigs();
            Map<String, DuplicateSubmitConfig> savedConfigMap = savedConfigs.stream()
                .collect(java.util.stream.Collectors.toMap(
                    DuplicateSubmitConfig::getMethodSignature, 
                    config -> config
                ));
            
            // 处理有注解的接口
            for (EndpointInfo endpoint : endpoints) {
                if (endpoint.isHasDuplicateSubmitAnnotation()) {
                    Map<String, Object> configMap = new HashMap<>();
                    configMap.put("methodSignature", endpoint.getMethodSignature());
                    
                    // 优先使用动态配置，否则使用注解配置
                    DuplicateSubmitConfig savedConfig = savedConfigMap.get(endpoint.getMethodSignature());
                    if (savedConfig != null) {
                        // 使用动态配置
                        configMap.put("enabled", savedConfig.isEnabled());
                        configMap.put("interval", savedConfig.getInterval());
                        configMap.put("dimension", savedConfig.getDimension());
                        configMap.put("keyExpression", savedConfig.getKeyExpression());
                        configMap.put("message", savedConfig.getMessage());
                        configMap.put("configType", "DYNAMIC");
                        configMap.put("operator", savedConfig.getOperator());
                        configMap.put("updateTime", savedConfig.getUpdateTime());
                    } else {
                        // 使用注解配置
                        DuplicateSubmit annotation = endpoint.getDuplicateSubmitAnnotation();
                        configMap.put("enabled", true);
                        configMap.put("interval", annotation.interval());
                        configMap.put("dimension", annotation.dimension().name());
                        configMap.put("keyExpression", annotation.keyExpression());
                        configMap.put("message", annotation.message());
                        configMap.put("configType", "ANNOTATION");
                    }
                    
                    configs.add(configMap);
                }
            }
            
            return ResponseEntity.ok(configs);
        } catch (Exception e) {
            log.error("获取防重复提交配置列表失败", e);
            return ResponseEntity.ok(Collections.emptyList());
        }
    }
    
    /**
     * 获取防重复提交配置
     */
    @GetMapping("/api/duplicate-submit/{methodSignature}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDuplicateSubmitConfig(
            @PathVariable(value = "methodSignature") String methodSignature, HttpSession session) {
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "未登录"));
        }
        
        try {
            if (duplicateSubmitConfigService == null) {
                // 返回默认配置
                Map<String, Object> config = new HashMap<>();
                config.put("methodSignature", methodSignature);
                config.put("enabled", false);
                config.put("interval", 5);
                config.put("dimension", "USER_METHOD");
                config.put("keyExpression", "");
                config.put("message", "请勿重复提交，请稍候再试");
                return ResponseEntity.ok(Map.of("success", true, "data", config));
            }
            
            DuplicateSubmitConfig duplicateConfig = duplicateSubmitConfigService.getConfig(methodSignature);
            
            if (duplicateConfig != null) {
                Map<String, Object> config = new HashMap<>();
                config.put("methodSignature", duplicateConfig.getMethodSignature());
                config.put("enabled", duplicateConfig.isEnabled());
                config.put("interval", duplicateConfig.getInterval());
                config.put("dimension", duplicateConfig.getDimension());
                config.put("keyExpression", duplicateConfig.getKeyExpression());
                config.put("message", duplicateConfig.getMessage());
                
                return ResponseEntity.ok(Map.of("success", true, "data", config));
            } else {
                // 返回默认配置
                Map<String, Object> config = new HashMap<>();
                config.put("methodSignature", methodSignature);
                config.put("enabled", false);
                config.put("interval", 5);
                config.put("dimension", "USER_METHOD");
                config.put("keyExpression", "");
                config.put("message", "请勿重复提交，请稍候再试");
                
                return ResponseEntity.ok(Map.of("success", true, "data", config));
            }
        } catch (Exception e) {
            log.error("获取防重复提交配置失败", e);
            return ResponseEntity.status(500)
                .body(Map.of("success", false, "message", "获取配置失败: " + e.getMessage()));
        }
    }
    
    /**
     * 保存防重复提交配置
     */
    @PostMapping("/api/duplicate-submit/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveDuplicateSubmitConfig(
            @RequestBody Map<String, Object> requestData, 
            HttpServletRequest request, HttpSession session) {
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "未登录"));
        }
        
        try {
            String methodSignature = (String) requestData.get("methodSignature");
            Boolean enabled = (Boolean) requestData.get("enabled");
            Integer interval = convertToInteger(requestData.get("interval"));
            String dimension = (String) requestData.get("dimension");
            String keyExpression = (String) requestData.get("keyExpression");
            String message = (String) requestData.get("message");
            
            if (methodSignature == null || methodSignature.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "方法签名不能为空"));
            }
            
            if (duplicateSubmitConfigService == null) {
                return ResponseEntity.status(500)
                    .body(Map.of("success", false, "message", "系统未启用防重复提交配置功能"));
            }
            
            String operator = (String) session.getAttribute(SESSION_USER_KEY);
            
            // 构建配置对象
            DuplicateSubmitConfig config = DuplicateSubmitConfig.builder()
                .methodSignature(methodSignature)
                .enabled(enabled != null ? enabled : false)
                .interval(interval != null ? interval : 5)
                .dimension(dimension != null ? dimension : "USER_METHOD")
                .keyExpression(keyExpression != null ? keyExpression : "")
                .message(message != null ? message : "请勿重复提交，请稍候再试")
                .operator(operator)
                .build();
            
            // 保存配置
            duplicateSubmitConfigService.saveConfig(config);
            
            // 记录操作日志
            log.info("Duplicate submit config saved: method={}, operator={}, ip={}", 
                methodSignature, operator, getClientIp(request));
            
            return ResponseEntity.ok(Map.of("success", true, "message", "防重复提交配置保存成功"));
            
        } catch (Exception e) {
            log.error("保存防重复提交配置失败", e);
            return ResponseEntity.status(500)
                .body(Map.of("success", false, "message", "保存配置失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除幂等性配置
     */
    @PostMapping("/api/idempotent/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteIdempotentConfig(
            @RequestBody Map<String, Object> requestData, 
            HttpServletRequest request, HttpSession session) {
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "未登录"));
        }
        
        try {
            String methodSignature = (String) requestData.get("methodSignature");
            if (methodSignature == null || methodSignature.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "方法签名不能为空"));
            }
            
            if (idempotentConfigService == null) {
                return ResponseEntity.status(500)
                    .body(Map.of("success", false, "message", "系统未启用幂等性配置功能"));
            }
            
            String operator = (String) session.getAttribute(SESSION_USER_KEY);
            
            // 删除配置
            boolean deleted = idempotentConfigService.deleteConfig(methodSignature, operator);
            
            if (deleted) {
                // 记录操作日志
                log.info("Idempotent config deleted: method={}, operator={}, ip={}", 
                    methodSignature, operator, getClientIp(request));
                
                return ResponseEntity.ok(Map.of("success", true, "message", "幂等性配置删除成功"));
            } else {
                return ResponseEntity.ok(Map.of("success", false, "message", "配置不存在或删除失败"));
            }
            
        } catch (Exception e) {
            log.error("删除幂等性配置失败", e);
            return ResponseEntity.status(500)
                .body(Map.of("success", false, "message", "删除配置失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除防重复提交配置
     */
    @PostMapping("/api/duplicate-submit/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteDuplicateSubmitConfig(
            @RequestBody Map<String, Object> requestData, 
            HttpServletRequest request, HttpSession session) {
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "未登录"));
        }
        
        try {
            String methodSignature = (String) requestData.get("methodSignature");
            if (methodSignature == null || methodSignature.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "方法签名不能为空"));
            }
            
            if (duplicateSubmitConfigService == null) {
                return ResponseEntity.status(500)
                    .body(Map.of("success", false, "message", "系统未启用防重复提交配置功能"));
            }
            
            String operator = (String) session.getAttribute(SESSION_USER_KEY);
            
            // 删除配置
            boolean deleted = duplicateSubmitConfigService.deleteConfig(methodSignature, operator);
            
            if (deleted) {
                // 记录操作日志
                log.info("Duplicate submit config deleted: method={}, operator={}, ip={}", 
                    methodSignature, operator, getClientIp(request));
                
                return ResponseEntity.ok(Map.of("success", true, "message", "防重复提交配置删除成功"));
            } else {
                return ResponseEntity.ok(Map.of("success", false, "message", "配置不存在或删除失败"));
            }
            
        } catch (Exception e) {
            log.error("删除防重复提交配置失败", e);
            return ResponseEntity.status(500)
                .body(Map.of("success", false, "message", "删除配置失败: " + e.getMessage()));
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
    
    /**
     * 手动清理API保护数据
     */
    @PostMapping("/api/cleanup")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> manualCleanup(HttpSession session, HttpServletRequest request) {
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "未登录"));
        }
        
        Map<String, Object> result = new HashMap<>();
        String username = (String) session.getAttribute(SESSION_USER_KEY);
        String clientIp = getClientIp(request);
        
        try {
            if (startupCleanupService != null) {
                long cleanedCount = startupCleanupService.manualCleanup();
                
                result.put("success", true);
                result.put("message", "数据清理成功");
                result.put("cleanedCount", cleanedCount);
                
                log.info("Manual cleanup performed by admin: user={}, IP={}, cleaned_keys={}", 
                    username, clientIp, cleanedCount);
            } else {
                result.put("success", false);
                result.put("message", "清理服务不可用（可能使用内存存储）");
            }
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "清理失败: " + e.getMessage());
            log.error("Manual cleanup failed: user={}, IP={}, error={}", username, clientIp, e.getMessage(), e);
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 添加安全配置信息到模型（不暴露敏感信息）
     */
    private void addSecurityConfigToModel(Model model) {
        Map<String, Object> securityConfig = new HashMap<>();
        securityConfig.put("headerCheckEnabled", adminProperties.getSecurity().isEnableHeaderCheck());
        securityConfig.put("headerName", adminProperties.getSecurity().getHeaderName());
        // 为了前端API调用能够正常工作，需要提供headerValue
        securityConfig.put("headerValue", adminProperties.getSecurity().getHeaderValue());
        
        model.addAttribute("securityConfig", securityConfig);
    }
}
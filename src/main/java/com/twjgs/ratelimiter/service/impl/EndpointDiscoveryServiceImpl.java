package com.twjgs.ratelimiter.service.impl;

import com.twjgs.ratelimiter.annotation.RateLimit;
import com.twjgs.ratelimiter.config.RateLimiterAdminProperties;
import com.twjgs.ratelimiter.model.DynamicRateLimitConfig;
import com.twjgs.ratelimiter.model.EndpointInfo;
import com.twjgs.ratelimiter.service.DynamicConfigService;
import com.twjgs.ratelimiter.service.EndpointDiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 接口发现服务实现
 * 
 * @author RateLimiter Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EndpointDiscoveryServiceImpl implements EndpointDiscoveryService {
    
    private final ApplicationContext applicationContext;
    private final DynamicConfigService dynamicConfigService;
    private final RateLimiterAdminProperties adminProperties;
    
    private List<EndpointInfo> cachedEndpoints;
    private long lastRefreshTime = 0;
    private static final long CACHE_DURATION = 5 * 60 * 1000; // 5分钟缓存
    
    @Override
    public List<EndpointInfo> discoverAllEndpoints() {
        // 检查缓存是否需要刷新
        if (cachedEndpoints == null || System.currentTimeMillis() - lastRefreshTime > CACHE_DURATION) {
            refreshCache();
        }
        return new ArrayList<>(cachedEndpoints);
    }
    
    @Override
    public EndpointInfo getEndpointByMethodSignature(String methodSignature) {
        return discoverAllEndpoints().stream()
                .filter(endpoint -> methodSignature.equals(endpoint.getMethodSignature()))
                .findFirst()
                .orElse(null);
    }
    
    @Override
    public List<EndpointInfo> searchEndpoints(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return discoverAllEndpoints();
        }
        
        String lowerKeyword = keyword.toLowerCase();
        return discoverAllEndpoints().stream()
                .filter(endpoint -> 
                    endpoint.getMethodSignature().toLowerCase().contains(lowerKeyword) ||
                    endpoint.getClassName().toLowerCase().contains(lowerKeyword) ||
                    endpoint.getMethodName().toLowerCase().contains(lowerKeyword) ||
                    (endpoint.getPath() != null && endpoint.getPath().toLowerCase().contains(lowerKeyword)) ||
                    (endpoint.getDescription() != null && endpoint.getDescription().toLowerCase().contains(lowerKeyword))
                )
                .collect(Collectors.toList());
    }
    
    @Override
    public Map<String, Object> getStatistics() {
        List<EndpointInfo> endpoints = discoverAllEndpoints();
        Map<String, Object> stats = new HashMap<>();
        
        // 总接口数
        stats.put("totalEndpoints", endpoints.size());
        
        // 有注解配置的接口数
        long annotationCount = endpoints.stream().filter(EndpointInfo::isHasAnnotation).count();
        stats.put("annotationConfigured", annotationCount);
        
        // 有动态配置的接口数
        long dynamicCount = endpoints.stream().filter(EndpointInfo::isHasDynamicConfig).count();
        stats.put("dynamicConfigured", dynamicCount);
        
        // 未配置的接口数
        long unconfiguredCount = endpoints.stream()
                .filter(endpoint -> !endpoint.isHasAnnotation() && !endpoint.isHasDynamicConfig())
                .count();
        stats.put("unconfigured", unconfiguredCount);
        
        // 按HTTP方法分组统计
        Map<String, Long> httpMethodStats = endpoints.stream()
                .filter(endpoint -> endpoint.getHttpMethod() != null)
                .collect(Collectors.groupingBy(EndpointInfo::getHttpMethod, Collectors.counting()));
        stats.put("httpMethodStats", httpMethodStats);
        
        // 按类分组统计
        Map<String, Long> classStats = endpoints.stream()
                .collect(Collectors.groupingBy(
                    endpoint -> endpoint.getClassName().substring(endpoint.getClassName().lastIndexOf('.') + 1),
                    Collectors.counting()
                ));
        stats.put("classStats", classStats);
        
        return stats;
    }
    
    @Override
    public Set<String> getAllPathPatterns() {
        return discoverAllEndpoints().stream()
                .filter(endpoint -> endpoint.getPath() != null)
                .map(EndpointInfo::getPath)
                .collect(Collectors.toSet());
    }
    
    @Override
    public void refreshCache() {
        try {
            log.info("Refreshing endpoint discovery cache...");
            
            List<EndpointInfo> endpoints = new ArrayList<>();
            
            // 获取所有Controller bean
            Map<String, Object> controllers = applicationContext.getBeansWithAnnotation(RestController.class);
            controllers.putAll(applicationContext.getBeansWithAnnotation(Controller.class));
            
            for (Map.Entry<String, Object> entry : controllers.entrySet()) {
                Object controller = entry.getValue();
                Class<?> controllerClass = controller.getClass();
                
                // 过滤管理页面相关的Controller
                if (shouldExcludeController(controllerClass)) {
                    log.debug("Excluding controller: {}", controllerClass.getName());
                    continue;
                }
                
                // 获取类级别的RequestMapping
                String classPath = "";
                RequestMapping classMapping = controllerClass.getAnnotation(RequestMapping.class);
                if (classMapping != null && classMapping.value().length > 0) {
                    classPath = classMapping.value()[0];
                }
                
                // 遍历所有方法
                Method[] methods = controllerClass.getDeclaredMethods();
                for (Method method : methods) {
                    EndpointInfo endpointInfo = createEndpointInfo(controllerClass, method, classPath);
                    if (endpointInfo != null && !shouldExcludeEndpoint(endpointInfo)) {
                        endpoints.add(endpointInfo);
                    }
                }
            }
            
            // 更新缓存
            this.cachedEndpoints = endpoints;
            this.lastRefreshTime = System.currentTimeMillis();
            
            log.info("Endpoint discovery cache refreshed, found {} endpoints", endpoints.size());
            
        } catch (Exception e) {
            log.error("Failed to refresh endpoint discovery cache", e);
        }
    }
    
    private EndpointInfo createEndpointInfo(Class<?> controllerClass, Method method, String classPath) {
        // 检查方法是否有HTTP映射注解
        String httpMethod = null;
        String methodPath = "";
        
        if (method.isAnnotationPresent(GetMapping.class)) {
            httpMethod = "GET";
            GetMapping mapping = method.getAnnotation(GetMapping.class);
            if (mapping.value().length > 0) {
                methodPath = mapping.value()[0];
            }
        } else if (method.isAnnotationPresent(PostMapping.class)) {
            httpMethod = "POST";
            PostMapping mapping = method.getAnnotation(PostMapping.class);
            if (mapping.value().length > 0) {
                methodPath = mapping.value()[0];
            }
        } else if (method.isAnnotationPresent(PutMapping.class)) {
            httpMethod = "PUT";
            PutMapping mapping = method.getAnnotation(PutMapping.class);
            if (mapping.value().length > 0) {
                methodPath = mapping.value()[0];
            }
        } else if (method.isAnnotationPresent(DeleteMapping.class)) {
            httpMethod = "DELETE";
            DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
            if (mapping.value().length > 0) {
                methodPath = mapping.value()[0];
            }
        } else if (method.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping mapping = method.getAnnotation(RequestMapping.class);
            if (mapping.method().length > 0) {
                httpMethod = mapping.method()[0].name();
            }
            if (mapping.value().length > 0) {
                methodPath = mapping.value()[0];
            }
        }
        
        // 如果没有HTTP映射注解，跳过
        if (httpMethod == null) {
            return null;
        }
        
        // 构建完整路径
        String fullPath = (classPath + methodPath).replaceAll("/+", "/");
        if (!fullPath.startsWith("/")) {
            fullPath = "/" + fullPath;
        }
        
        // 创建EndpointInfo
        EndpointInfo endpointInfo = new EndpointInfo();
        endpointInfo.setClassName(controllerClass.getName());
        endpointInfo.setMethodName(method.getName());
        endpointInfo.setHttpMethod(httpMethod);
        endpointInfo.setPath(fullPath);
        
        // 生成方法签名
        String methodSignature = generateMethodSignature(controllerClass, method);
        endpointInfo.setMethodSignature(methodSignature);
        
        // 检查是否有RateLimit注解
        RateLimit rateLimitAnnotation = method.getAnnotation(RateLimit.class);
        if (rateLimitAnnotation != null) {
            endpointInfo.setRateLimitAnnotation(rateLimitAnnotation);
            endpointInfo.setHasAnnotation(true);
            endpointInfo.setEffectiveConfigType("ANNOTATION");
            endpointInfo.setEffectiveConfig(rateLimitAnnotation);
        }
        
        // 检查是否有动态配置
        DynamicRateLimitConfig dynamicConfig = dynamicConfigService.getDynamicConfig(methodSignature);
        if (dynamicConfig != null) {
            endpointInfo.setDynamicConfig(dynamicConfig);
            endpointInfo.setHasDynamicConfig(true);
            // 动态配置优先级更高
            endpointInfo.setEffectiveConfigType("DYNAMIC");
            endpointInfo.setEffectiveConfig(dynamicConfig);
        }
        
        // 如果都没有配置
        if (!endpointInfo.isHasAnnotation() && !endpointInfo.isHasDynamicConfig()) {
            endpointInfo.setEffectiveConfigType("NONE");
        }
        
        return endpointInfo;
    }
    
    private String generateMethodSignature(Class<?> clazz, Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(clazz.getName()).append(".").append(method.getName()).append("(");
        
        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(paramTypes[i].getSimpleName());
        }
        
        sb.append(")");
        return sb.toString();
    }
    
    /**
     * 判断是否应该排除某个Controller
     */
    private boolean shouldExcludeController(Class<?> controllerClass) {
        String className = controllerClass.getName();
        RateLimiterAdminProperties.Discovery discovery = adminProperties.getDiscovery();
        
        // 排除限流管理相关的Controller
        if (discovery.isExcludeAdminEndpoints() && 
            (className.contains("RateLimiterManagement") || className.contains("rateLimiter.controller"))) {
            return true;
        }
        
        // 排除自定义包名前缀
        if (discovery.getExcludePackages() != null && !discovery.getExcludePackages().trim().isEmpty()) {
            String[] packages = discovery.getExcludePackages().split(",");
            for (String pkg : packages) {
                if (className.startsWith(pkg.trim())) {
                    return true;
                }
            }
        }
        
        // 排除包含指定关键字的Controller
        if (discovery.getExcludeControllerKeywords() != null && !discovery.getExcludeControllerKeywords().trim().isEmpty()) {
            String[] keywords = discovery.getExcludeControllerKeywords().split(",");
            String simpleClassName = controllerClass.getSimpleName();
            for (String keyword : keywords) {
                if (simpleClassName.contains(keyword.trim())) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 判断是否应该排除某个接口
     */
    private boolean shouldExcludeEndpoint(EndpointInfo endpointInfo) {
        String path = endpointInfo.getPath();
        RateLimiterAdminProperties.Discovery discovery = adminProperties.getDiscovery();
        
        if (path == null) {
            return false;
        }
        
        // 排除管理页面路径
        if (discovery.isExcludeAdminEndpoints()) {
            String basePath = adminProperties.getBasePath();
            if (path.startsWith(basePath)) {
                return true;
            }
        }
        
        // 排除Spring Boot Actuator端点
        if (discovery.isExcludeActuatorEndpoints() && 
            (path.startsWith("/actuator") || path.startsWith("/management"))) {
            return true;
        }
        
        // 排除错误页面
        if (discovery.isExcludeErrorEndpoints() && path.startsWith("/error")) {
            return true;
        }
        
        // 排除静态资源路径
        if (discovery.isExcludeStaticResourceEndpoints() && 
            (path.startsWith("/static") || path.startsWith("/public") || 
             path.startsWith("/resources") || path.startsWith("/META-INF") ||
             path.contains("/swagger") || path.contains("/api-docs") || 
             path.contains("/webjars"))) {
            return true;
        }
        
        // 排除自定义路径
        if (discovery.getExcludePaths() != null && !discovery.getExcludePaths().trim().isEmpty()) {
            String[] paths = discovery.getExcludePaths().split(",");
            for (String excludePath : paths) {
                String trimmedPath = excludePath.trim();
                if (path.startsWith(trimmedPath) || path.equals(trimmedPath)) {
                    return true;
                }
            }
        }
        
        return false;
    }
}
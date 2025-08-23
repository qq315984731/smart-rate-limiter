package com.twjgs.ratelimiter.config;

import com.twjgs.ratelimiter.controller.RateLimiterManagementController;
import com.twjgs.ratelimiter.interceptor.AdminSecurityInterceptor;
import com.twjgs.ratelimiter.interceptor.RateLimitInterceptor;
import com.twjgs.ratelimiter.model.DynamicRateLimitConfig;
import com.twjgs.ratelimiter.service.*;
import com.twjgs.ratelimiter.service.impl.*;
import com.twjgs.ratelimiter.util.KeyGenerator;
import com.twjgs.ratelimiter.util.SpelExpressionEvaluator;
import com.twjgs.ratelimiter.util.CustomStrategyProcessor;
import com.twjgs.ratelimiter.interceptor.IdempotentInterceptor;
import com.twjgs.ratelimiter.advice.IdempotentResponseAdvice;
import com.twjgs.ratelimiter.service.IdempotentService;
import com.twjgs.ratelimiter.service.StartupCleanupService;
import com.twjgs.ratelimiter.service.impl.RedisIdempotentService;
import com.twjgs.ratelimiter.service.impl.MemoryIdempotentService;
import com.twjgs.ratelimiter.interceptor.DuplicateSubmitInterceptor;
import com.twjgs.ratelimiter.service.DuplicateSubmitService;
import com.twjgs.ratelimiter.service.impl.RedisDuplicateSubmitService;
import com.twjgs.ratelimiter.service.impl.MemoryDuplicateSubmitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-configuration for Smart Rate Limiter
 * 
 * <p>Automatically configures rate limiting components based on available
 * dependencies and configuration properties.
 * 
 * @author Smart Rate Limiter Team
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties({RateLimiterProperties.class, RateLimiterAdminProperties.class, ApiProtectionProperties.class})
@ConditionalOnProperty(prefix = "smart.rate-limiter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimiterAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterAutoConfiguration.class);

    /**
     * Common utilities shared across all configurations
     */
    @Bean
    @ConditionalOnMissingBean
    public SpelExpressionEvaluator spelExpressionEvaluator() {
        return new SpelExpressionEvaluator();
    }

    @Bean
    @ConditionalOnMissingBean
    public CustomStrategyProcessor customStrategyProcessor(SpelExpressionEvaluator spelExpressionEvaluator) {
        return new CustomStrategyProcessor(spelExpressionEvaluator);
    }

    /**
     * Configuration for Redis-based rate limiting
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "smart.rate-limiter", name = "storage-type", havingValue = "redis", matchIfMissing = false)
    static class RedisRateLimiterConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public RateLimitService redisRateLimitService(StringRedisTemplate redisTemplate, 
                                                     RateLimiterProperties properties) {
            return new RedisRateLimitService(redisTemplate, properties);
        }
    }

    /**
     * Configuration for memory-based rate limiting
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "smart.rate-limiter", name = "storage-type", havingValue = "memory", matchIfMissing = true)
    static class MemoryRateLimiterConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public RateLimitService memoryRateLimitService(RateLimiterProperties properties) {
            return new MemoryRateLimitService(properties);
        }
    }

    /**
     * Fallback configuration when Redis is not available
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(RateLimitService.class)
    static class FallbackRateLimiterConfiguration {

        @Bean
        public RateLimitService fallbackRateLimitService(RateLimiterProperties properties) {
            log.warn("Redis not available, falling back to memory-based rate limiting");
            return new MemoryRateLimitService(properties);
        }
    }

    /**
     * Core rate limiting components
     */
    @Configuration(proxyBeanMethods = false)
    static class CoreConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public UserIdResolver userIdResolver() {
            return new DefaultUserIdResolver();
        }

        @Bean
        @ConditionalOnMissingBean
        public IpResolver ipResolver() {
            return new DefaultIpResolver();
        }

        @Bean
        @ConditionalOnMissingBean
        public KeyGenerator keyGenerator(RateLimiterProperties properties, CustomStrategyProcessor customStrategyProcessor) {
            return new KeyGenerator(properties, customStrategyProcessor);
        }

        @Bean
        @ConditionalOnMissingBean
        public RateLimitInterceptor rateLimitInterceptor(RateLimitService rateLimitService,
                                                        RateLimiterProperties properties,
                                                        UserIdResolver userIdResolver,
                                                        IpResolver ipResolver,
                                                        KeyGenerator keyGenerator,
                                                        SpelExpressionEvaluator spelEvaluator,
                                                        @org.springframework.beans.factory.annotation.Autowired(required = false) 
                                                        DynamicConfigService dynamicConfigService) {
            return new RateLimitInterceptor(rateLimitService, properties, userIdResolver, 
                                          ipResolver, keyGenerator, spelEvaluator, dynamicConfigService);
        }
    }

    /**
     * Web MVC configuration to register the rate limit interceptor
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(WebMvcConfigurer.class)
    static class WebMvcConfiguration implements WebMvcConfigurer {

        private final RateLimitInterceptor rateLimitInterceptor;

        public WebMvcConfiguration(RateLimitInterceptor rateLimitInterceptor) {
            this.rateLimitInterceptor = rateLimitInterceptor;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(rateLimitInterceptor)
                    .addPathPatterns("/**")
                    .order(Integer.MIN_VALUE + 1000); // High priority but not highest
        }
    }

    /**
     * Configuration for admin management functionality
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "smart.rate-limiter.admin.enabled", havingValue = "true", matchIfMissing = false)
    static class AdminConfiguration {

        /**
         * Redis-based admin services configuration
         */
        @Configuration(proxyBeanMethods = false)
        @ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
        static class RedisAdminConfiguration {
            
            @Bean
            @ConditionalOnMissingBean
            public DynamicConfigService dynamicConfigService(RedisTemplate<String, Object> redisTemplate) {
                return new DynamicConfigServiceImpl(redisTemplate);
            }
        }

        /**
         * Fallback configuration when Redis is not available
         */
        @Configuration(proxyBeanMethods = false)
        @ConditionalOnMissingBean(DynamicConfigService.class)
        static class FallbackAdminConfiguration {

            @Bean
            public DynamicConfigService dynamicConfigService() {
                log.warn("Redis not available for admin services, using memory-based dynamic config service");
                // Create a simple memory-based implementation
                return new DynamicConfigService() {
                    private final java.util.Map<String, DynamicRateLimitConfig> configs = new java.util.concurrent.ConcurrentHashMap<>();

                    @Override
                    public DynamicRateLimitConfig getDynamicConfig(String methodSignature) {
                        return configs.get(methodSignature);
                    }

                    @Override
                    public void saveDynamicConfig(String methodSignature, DynamicRateLimitConfig config, String operator) {
                        configs.put(methodSignature, config);
                    }

                    @Override
                    public void deleteDynamicConfig(String methodSignature, String operator) {
                        configs.remove(methodSignature);
                    }

                    @Override
                    public java.util.Map<String, DynamicRateLimitConfig> getAllDynamicConfigs() {
                        return new java.util.HashMap<>(configs);
                    }

                    @Override
                    public void savePathPatternConfig(String pathPattern, String httpMethod, DynamicRateLimitConfig config, String operator) {
                        String key = pathPattern + ":" + httpMethod;
                        configs.put(key, config);
                    }

                    @Override
                    public boolean hasConfig(String methodSignature) {
                        return configs.containsKey(methodSignature);
                    }

                    @Override
                    public void cleanExpiredConfigs() {
                        // Remove expired configs
                        configs.entrySet().removeIf(entry -> {
                            DynamicRateLimitConfig config = entry.getValue();
                            return config.getTemporary() && config.getExpireTime() != null 
                                && config.getExpireTime().isBefore(java.time.LocalDateTime.now());
                        });
                    }
                };
            }
        }

        @Bean
        @ConditionalOnMissingBean
        public FileLogService fileLogService(RateLimiterAdminProperties adminProperties) {
            return new FileLogServiceImpl(adminProperties);
        }

        @Bean
        @ConditionalOnMissingBean
        public EndpointDiscoveryService endpointDiscoveryService(ApplicationContext applicationContext,
                                                                DynamicConfigService dynamicConfigService,
                                                                RateLimiterAdminProperties adminProperties) {
            return new EndpointDiscoveryServiceImpl(applicationContext, dynamicConfigService, adminProperties);
        }

        @Bean
        @ConditionalOnMissingBean
        public RateLimiterManagementController rateLimiterManagementController(
                DynamicConfigService dynamicConfigService,
                EndpointDiscoveryService endpointDiscoveryService,
                FileLogService fileLogService,
                RateLimiterAdminProperties adminProperties,
                @Autowired(required = false) IdempotentConfigService idempotentConfigService,
                @Autowired(required = false) DuplicateSubmitConfigService duplicateSubmitConfigService,
                @Autowired(required = false) StartupCleanupService startupCleanupService) {
            return new RateLimiterManagementController(dynamicConfigService, endpointDiscoveryService, 
                    fileLogService, adminProperties, idempotentConfigService, duplicateSubmitConfigService, startupCleanupService);
        }

        @Bean
        @ConditionalOnMissingBean
        public AdminSecurityInterceptor adminSecurityInterceptor(RateLimiterAdminProperties adminProperties,
                                                                ObjectMapper objectMapper) {
            return new AdminSecurityInterceptor(adminProperties, objectMapper);
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(name = "smart.rate-limiter.admin.enabled", havingValue = "true", matchIfMissing = false)
        public IdempotentConfigService idempotentConfigService() {
            return new com.twjgs.ratelimiter.service.impl.MemoryIdempotentConfigServiceImpl();
        }

        @Bean
        @ConditionalOnMissingBean  
        @ConditionalOnProperty(name = "smart.rate-limiter.admin.enabled", havingValue = "true", matchIfMissing = false)
        public DuplicateSubmitConfigService duplicateSubmitConfigService() {
            return new com.twjgs.ratelimiter.service.impl.MemoryDuplicateSubmitConfigServiceImpl();
        }

        /**
         * Admin security MVC configuration to register the security interceptor
         */
        @Configuration(proxyBeanMethods = false)
        @ConditionalOnClass(WebMvcConfigurer.class)
        @ConditionalOnBean(AdminSecurityInterceptor.class)
        static class AdminSecurityWebMvcConfiguration implements WebMvcConfigurer {

            private final AdminSecurityInterceptor adminSecurityInterceptor;
            private final RateLimiterAdminProperties adminProperties;

            public AdminSecurityWebMvcConfiguration(AdminSecurityInterceptor adminSecurityInterceptor,
                                                   RateLimiterAdminProperties adminProperties) {
                this.adminSecurityInterceptor = adminSecurityInterceptor;
                this.adminProperties = adminProperties;
            }

            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                String basePath = adminProperties.getBasePath();
                
                registry.addInterceptor(adminSecurityInterceptor)
                        .addPathPatterns(basePath + "/api/**")  // 只拦截API请求
                        .excludePathPatterns(
                            basePath + "/login",          // 排除登录页面
                            basePath + "/logout",         // 排除登出请求
                            basePath + "/dashboard",      // 排除控制台页面
                            basePath + "/endpoints",      // 排除接口管理页面
                            basePath + "/config",         // 排除配置页面
                            basePath + "/",               // 排除根路径
                            basePath,                     // 排除基础路径
                            basePath + "/static/**",      // 排除静态资源
                            basePath + "/css/**",         // 排除CSS文件
                            basePath + "/js/**",          // 排除JS文件
                            basePath + "/images/**"       // 排除图片文件
                        )
                        .order(Integer.MIN_VALUE + 500); // 更高优先级，在限流拦截器之前
            }
        }
    }

    /**
     * Configuration for API Protection Suite (Idempotent, DuplicateSubmit, RequestDedup)
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "smart.api-protection", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class ApiProtectionConfiguration {

        /**
         * Redis-based idempotent services configuration
         */
        @Configuration(proxyBeanMethods = false)
        @ConditionalOnClass(RedisTemplate.class)
        @ConditionalOnProperty(prefix = "smart.api-protection", name = "storage.type", havingValue = "redis", matchIfMissing = false)
        static class RedisApiProtectionConfiguration {
            
            @Bean
            @ConditionalOnMissingBean
            @ConditionalOnProperty(prefix = "smart.api-protection.idempotent", name = "enabled", havingValue = "true", matchIfMissing = true)
            public IdempotentService redisIdempotentService(RedisTemplate<String, Object> redisTemplate,
                                                           ObjectMapper objectMapper,
                                                           RateLimiterProperties rateLimiterProperties,
                                                           ApiProtectionProperties apiProtectionProperties,
                                                           @Autowired(required = false) RedisExpirationService redisExpirationService) {
                return new RedisIdempotentService(redisTemplate, objectMapper, rateLimiterProperties, apiProtectionProperties, redisExpirationService);
            }
            
            @Bean
            @ConditionalOnMissingBean
            @ConditionalOnProperty(prefix = "smart.api-protection.duplicate-submit", name = "enabled", havingValue = "true", matchIfMissing = true)
            public DuplicateSubmitService redisDuplicateSubmitService(RedisTemplate<String, String> redisTemplate,
                                                                     ObjectMapper objectMapper,
                                                                     ApiProtectionProperties apiProtectionProperties) {
                return new RedisDuplicateSubmitService(redisTemplate, objectMapper, apiProtectionProperties);
            }

            @Bean
            @ConditionalOnMissingBean
            public StartupCleanupService startupCleanupService(RedisTemplate<String, Object> redisTemplate,
                                                              ApiProtectionProperties apiProtectionProperties) {
                return new StartupCleanupService(redisTemplate, apiProtectionProperties);
            }
        }

        /**
         * Memory-based idempotent services configuration
         */
        @Configuration(proxyBeanMethods = false)
        @ConditionalOnProperty(prefix = "smart.api-protection", name = "storage.type", havingValue = "memory", matchIfMissing = true)
        static class MemoryApiProtectionConfiguration {
            
            @Bean
            @ConditionalOnMissingBean
            @ConditionalOnProperty(prefix = "smart.api-protection.idempotent", name = "enabled", havingValue = "true", matchIfMissing = true)
            public IdempotentService memoryIdempotentService() {
                return new MemoryIdempotentService();
            }
            
            @Bean
            @ConditionalOnMissingBean
            @ConditionalOnProperty(prefix = "smart.api-protection.duplicate-submit", name = "enabled", havingValue = "true", matchIfMissing = true)
            public DuplicateSubmitService memoryDuplicateSubmitService() {
                return new MemoryDuplicateSubmitService();
            }
        }

        /**
         * Fallback configuration when Redis is not available
         */
        @Configuration(proxyBeanMethods = false)
        @ConditionalOnMissingBean(IdempotentService.class)
        @ConditionalOnProperty(prefix = "smart.api-protection.idempotent", name = "enabled", havingValue = "true", matchIfMissing = true)
        static class FallbackApiProtectionConfiguration {

            @Bean
            public IdempotentService fallbackIdempotentService() {
                log.warn("Redis not available for API protection, using memory-based idempotent service");
                return new MemoryIdempotentService();
            }
        }

        /**
         * Fallback duplicate submit service configuration when Redis is not available
         */
        @Configuration(proxyBeanMethods = false)
        @ConditionalOnMissingBean(DuplicateSubmitService.class)
        @ConditionalOnProperty(prefix = "smart.api-protection.duplicate-submit", name = "enabled", havingValue = "true", matchIfMissing = true)
        static class FallbackDuplicateSubmitConfiguration {

            @Bean
            public DuplicateSubmitService fallbackDuplicateSubmitService() {
                log.warn("Redis not available for API protection, using memory-based duplicate submit service");
                return new MemoryDuplicateSubmitService();
            }
        }

        /**
         * Idempotent interceptor configuration
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "smart.api-protection.idempotent", name = "enabled", havingValue = "true", matchIfMissing = true)
        public IdempotentInterceptor idempotentInterceptor(IdempotentService idempotentService,
                                                          @Autowired(required = false) IdempotentConfigService idempotentConfigService,
                                                          UserIdResolver userIdResolver,
                                                          SpelExpressionEvaluator spelEvaluator,
                                                          CustomStrategyProcessor customStrategyProcessor,
                                                          ObjectMapper objectMapper,
                                                          ApiProtectionProperties apiProtectionProperties) {
            int order = apiProtectionProperties.getInterceptorOrder().getIdempotent();
            return new IdempotentInterceptor(idempotentService, idempotentConfigService, userIdResolver, spelEvaluator, customStrategyProcessor, objectMapper, order);
        }

        /**
         * Idempotent response advice configuration
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "smart.api-protection.idempotent", name = "enabled", havingValue = "true", matchIfMissing = true)
        public IdempotentResponseAdvice idempotentResponseAdvice(IdempotentService idempotentService,
                                                               ObjectMapper objectMapper) {
            return new IdempotentResponseAdvice(idempotentService, objectMapper);
        }

        /**
         * Duplicate submit interceptor configuration
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "smart.api-protection.duplicate-submit", name = "enabled", havingValue = "true", matchIfMissing = true)
        public DuplicateSubmitInterceptor duplicateSubmitInterceptor(DuplicateSubmitService duplicateSubmitService,
                                                                    @Autowired(required = false) DuplicateSubmitConfigService duplicateSubmitConfigService,
                                                                    SpelExpressionEvaluator spelEvaluator,
                                                                    CustomStrategyProcessor customStrategyProcessor,
                                                                    UserIdResolver userIdResolver,
                                                                    ApiProtectionProperties apiProtectionProperties) {
            int order = apiProtectionProperties.getInterceptorOrder().getDuplicateSubmit();
            return new DuplicateSubmitInterceptor(duplicateSubmitService, duplicateSubmitConfigService, spelEvaluator, customStrategyProcessor, userIdResolver, order);
        }

        /**
         * Web MVC configuration to register the API protection interceptors
         */
        @Configuration(proxyBeanMethods = false)
        @ConditionalOnClass(WebMvcConfigurer.class)
        static class ApiProtectionWebMvcConfiguration implements WebMvcConfigurer {

            private final IdempotentInterceptor idempotentInterceptor;
            private final DuplicateSubmitInterceptor duplicateSubmitInterceptor;

            public ApiProtectionWebMvcConfiguration(
                    @org.springframework.beans.factory.annotation.Autowired(required = false) 
                    IdempotentInterceptor idempotentInterceptor,
                    @org.springframework.beans.factory.annotation.Autowired(required = false) 
                    DuplicateSubmitInterceptor duplicateSubmitInterceptor) {
                this.idempotentInterceptor = idempotentInterceptor;
                this.duplicateSubmitInterceptor = duplicateSubmitInterceptor;
            }

            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                if (idempotentInterceptor != null) {
                    registry.addInterceptor(idempotentInterceptor)
                            .addPathPatterns("/**");
                }
                
                if (duplicateSubmitInterceptor != null) {
                    registry.addInterceptor(duplicateSubmitInterceptor)
                            .addPathPatterns("/**");
                }
            }
        }
    }
}
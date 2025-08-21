package com.twjgs.ratelimiter.config;

import com.twjgs.ratelimiter.controller.RateLimiterManagementController;
import com.twjgs.ratelimiter.interceptor.RateLimitInterceptor;
import com.twjgs.ratelimiter.model.DynamicRateLimitConfig;
import com.twjgs.ratelimiter.service.*;
import com.twjgs.ratelimiter.service.impl.*;
import com.twjgs.ratelimiter.util.KeyGenerator;
import com.twjgs.ratelimiter.util.SpelExpressionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@EnableConfigurationProperties({RateLimiterProperties.class, RateLimiterAdminProperties.class})
@ConditionalOnProperty(prefix = "smart.rate-limiter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimiterAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterAutoConfiguration.class);

    /**
     * Configuration for Redis-based rate limiting
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "smart.rate-limiter", name = "storage-type", havingValue = "redis", matchIfMissing = true)
    static class RedisRateLimiterConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public RateLimitService redisRateLimitService(StringRedisTemplate redisTemplate, 
                                                     RateLimiterProperties properties) {
            log.info("Configuring Redis-based rate limiting service");
            return new RedisRateLimitService(redisTemplate, properties);
        }
    }

    /**
     * Configuration for memory-based rate limiting
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "smart.rate-limiter", name = "storage-type", havingValue = "memory")
    static class MemoryRateLimiterConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public RateLimitService memoryRateLimitService(RateLimiterProperties properties) {
            log.info("Configuring memory-based rate limiting service");
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
        public KeyGenerator keyGenerator(RateLimiterProperties properties) {
            return new KeyGenerator(properties);
        }

        @Bean
        @ConditionalOnMissingBean
        public SpelExpressionEvaluator spelExpressionEvaluator() {
            return new SpelExpressionEvaluator();
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
            log.info("Registering rate limit interceptor");
            registry.addInterceptor(rateLimitInterceptor)
                    .addPathPatterns("/**")
                    .order(Integer.MIN_VALUE + 1000); // High priority but not highest
        }
    }

    /**
     * Configuration for admin management functionality
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "rate-limiter.admin.enabled", havingValue = "true", matchIfMissing = false)
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
                log.info("Configuring Redis-based dynamic config service for admin management");
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
                        log.info("Config saved: method={}, operator={}", methodSignature, operator);
                    }

                    @Override
                    public void deleteDynamicConfig(String methodSignature, String operator) {
                        configs.remove(methodSignature);
                        log.info("Config deleted: method={}, operator={}", methodSignature, operator);
                    }

                    @Override
                    public java.util.Map<String, DynamicRateLimitConfig> getAllDynamicConfigs() {
                        return new java.util.HashMap<>(configs);
                    }

                    @Override
                    public void savePathPatternConfig(String pathPattern, String httpMethod, DynamicRateLimitConfig config, String operator) {
                        String key = pathPattern + ":" + httpMethod;
                        configs.put(key, config);
                        log.info("Path pattern config saved: key={}, operator={}", key, operator);
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
            log.info("Configuring file log service for admin operations");
            return new FileLogServiceImpl(adminProperties);
        }

        @Bean
        @ConditionalOnMissingBean
        public EndpointDiscoveryService endpointDiscoveryService(ApplicationContext applicationContext,
                                                                DynamicConfigService dynamicConfigService,
                                                                RateLimiterAdminProperties adminProperties) {
            log.info("Configuring endpoint discovery service for admin management");
            return new EndpointDiscoveryServiceImpl(applicationContext, dynamicConfigService, adminProperties);
        }

        @Bean
        @ConditionalOnMissingBean
        public RateLimiterManagementController rateLimiterManagementController(
                DynamicConfigService dynamicConfigService,
                EndpointDiscoveryService endpointDiscoveryService,
                FileLogService fileLogService,
                RateLimiterAdminProperties adminProperties) {
            log.info("Configuring rate limiter management controller");
            return new RateLimiterManagementController(dynamicConfigService, endpointDiscoveryService, 
                    fileLogService, adminProperties);
        }
    }
}
package com.twjgs.ratelimiter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * API保护套件配置属性
 * 
 * @author Smart Rate Limiter Team
 * @since 1.1.0
 */
@Data
@ConfigurationProperties(prefix = "smart.api-protection")
public class ApiProtectionProperties {

    /**
     * 是否启用API保护套件
     */
    private boolean enabled = true;

    /**
     * 幂等性控制配置
     */
    private Idempotent idempotent = new Idempotent();

    /**
     * 防重复提交配置
     */
    private DuplicateSubmit duplicateSubmit = new DuplicateSubmit();

    /**
     * 请求去重配置
     */
    private RequestDedup requestDedup = new RequestDedup();

    /**
     * 存储配置
     */
    private Storage storage = new Storage();

    /**
     * 监控配置
     */
    private Monitoring monitoring = new Monitoring();

    /**
     * 服务启动时是否清理幂等性和防重复提交的历史数据
     * <p>默认启用。这确保服务重启后从全新状态开始，特别适用于分布式微服务环境。
     */
    private boolean startupCleanupEnabled = true;

    /**
     * 服务重启时是否清理动态配置数据
     * <p>默认启用。这确保动态配置不会在重启后保留，避免配置混乱。
     */
    private boolean startupCleanupDynamicConfig = true;

    /**
     * 键前缀配置
     */
    private KeyPrefix keyPrefix = new KeyPrefix();

    /**
     * 拦截器执行优先级配置
     */
    private InterceptorOrder interceptorOrder = new InterceptorOrder();

    @Data
    public static class Idempotent {
        /**
         * 是否启用幂等性控制
         */
        private boolean enabled = true;

        /**
         * 默认超时时间（秒）
         */
        private int defaultTimeout = 300;

        /**
         * 键前缀
         */
        private String keyPrefix = "idempotent:";

        /**
         * 是否启用结果缓存
         */
        private boolean resultCacheEnabled = true;

        /**
         * 最大缓存结果大小（字节）
         */
        private int maxResultSize = 10240; // 10KB
    }

    @Data
    public static class DuplicateSubmit {
        /**
         * 是否启用防重复提交
         */
        private boolean enabled = true;

        /**
         * 默认时间间隔（秒）
         */
        private int defaultInterval = 5;

        /**
         * 键前缀
         */
        private String keyPrefix = "duplicate:";
    }

    @Data
    public static class RequestDedup {
        /**
         * 是否启用请求去重
         */
        private boolean enabled = true;

        /**
         * 默认时间窗口（秒）
         */
        private int defaultWindow = 60;

        /**
         * 键前缀
         */
        private String keyPrefix = "dedup:";

        /**
         * 是否默认缓存结果
         */
        private boolean defaultCacheResult = true;

        /**
         * 默认结果缓存时间（秒）
         */
        private int defaultResultCacheTime = 300;
    }

    @Data
    public static class Storage {
        /**
         * 存储类型：redis, memory
         */
        private String type = "redis";

        /**
         * 键分隔符
         */
        private String keySeparator = ":";
    }

    @Data
    public static class Monitoring {
        /**
         * 是否启用监控
         */
        private boolean enabled = true;

        /**
         * 是否启用指标收集
         */
        private boolean metricsEnabled = true;

        /**
         * 是否包含详细标签
         */
        private boolean includeDetailedTags = false;
    }

    /**
     * 键前缀配置类
     */
    @Data
    public static class KeyPrefix {
        /**
         * 幂等性键前缀
         * <p>默认: "smart:idempotent:"
         */
        private String idempotent = "smart:idempotent:";

        /**
         * 防重复提交键前缀
         * <p>默认: "smart:duplicate:"
         */
        private String duplicateSubmit = "smart:duplicate:";

        /**
         * 动态配置键前缀
         * <p>默认: "smart:config:"
         */
        private String dynamicConfig = "smart:config:";

        /**
         * 应用程序标识符，用于多应用共享Redis时区分
         * <p>如果设置，会自动添加到所有键前缀中
         */
        private String applicationId;

        /**
         * 获取完整的幂等性键前缀
         */
        public String getFullIdempotentPrefix() {
            return applicationId != null ? applicationId + ":" + idempotent : idempotent;
        }

        /**
         * 获取完整的防重复提交键前缀
         */
        public String getFullDuplicateSubmitPrefix() {
            return applicationId != null ? applicationId + ":" + duplicateSubmit : duplicateSubmit;
        }

        /**
         * 获取完整的动态配置键前缀
         */
        public String getFullDynamicConfigPrefix() {
            return applicationId != null ? applicationId + ":" + dynamicConfig : dynamicConfig;
        }
    }

    /**
     * 拦截器优先级配置
     */
    @Data
    public static class InterceptorOrder {
        /**
         * 幂等性拦截器优先级
         * <p>数值越小优先级越高。默认: 100
         */
        private int idempotent = 100;

        /**
         * 防重复提交拦截器优先级
         * <p>数值越小优先级越高。默认: 200
         */
        private int duplicateSubmit = 200;

        /**
         * 限流拦截器优先级
         * <p>数值越小优先级越高。默认: 50
         */
        private int rateLimit = 50;
    }
}
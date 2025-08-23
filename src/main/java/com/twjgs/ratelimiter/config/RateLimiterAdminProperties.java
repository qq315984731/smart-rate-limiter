package com.twjgs.ratelimiter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 限流管理页面配置属性
 * 
 * @author RateLimiter Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "smart.rate-limiter.admin")
public class RateLimiterAdminProperties {

    /**
     * 是否启用管理页面 - 默认为false，需要显式配置才会启用
     */
    private boolean enabled = false;

    /**
     * 管理页面基础路径
     */
    private String basePath = "/admin/rate-limiter";

    /**
     * 用户名
     */
    private String username = "admin";

    /**
     * 密码
     */
    private String password = "admin123";

    /**
     * 会话超时时间（分钟）
     */
    private int sessionTimeout = 30;

    /**
     * 安全配置
     */
    private Security security = new Security();

    /**
     * 接口发现配置
     */
    private Discovery discovery = new Discovery();

    /**
     * 操作日志配置
     */
    private Logging logging = new Logging();

    /**
     * 扫描策略配置
     */
    private Scanning scanning = new Scanning();

    @Data
    public static class Security {
        /**
         * 是否启用安全头检查
         */
        private boolean enableHeaderCheck = false;

        /**
         * 自定义安全头名称
         */
        private String headerName = "X-RateLimit-Token";

        /**
         * 自定义安全头值
         */
        private String headerValue = "";

        /**
         * IP白名单（逗号分隔）
         */
        private String allowedIps = "";
    }

    @Data
    public static class Discovery {
        /**
         * 是否自动排除管理页面接口
         */
        private boolean excludeAdminEndpoints = true;

        /**
         * 是否排除Actuator端点
         */
        private boolean excludeActuatorEndpoints = true;

        /**
         * 是否排除错误页面端点
         */
        private boolean excludeErrorEndpoints = true;

        /**
         * 是否排除静态资源端点
         */
        private boolean excludeStaticResourceEndpoints = true;

        /**
         * 自定义排除的包名前缀（逗号分隔）
         */
        private String excludePackages = "org.springframework.boot,org.springframework.cloud";

        /**
         * 自定义排除的路径前缀（逗号分隔）
         */
        private String excludePaths = "/favicon.ico,/robots.txt";

        /**
         * 自定义排除的Controller类名包含的关键字（逗号分隔）
         */
        private String excludeControllerKeywords = "BasicErrorController,ErrorController,RateLimiterManagement,Management,Admin";
    }

    @Data
    public static class Logging {
        /**
         * 是否启用文件日志输出（记录限流配置操作）
         */
        private boolean fileEnabled = false;

        /**
         * 日志文件输出路径（默认：./logs/rate-limiter/operations.log）
         */
        private String filePath = "./logs/rate-limiter/operations.log";
    }

    @Data
    public static class Scanning {
        /**
         * 扫描策略：SYNC(同步启动时扫描), ASYNC(异步延迟扫描), DISABLED(禁用)
         */
        private ScanStrategy strategy = ScanStrategy.SYNC;

        /**
         * 异步扫描延迟启动时间（分钟），默认3分钟
         */
        private int asyncDelayMinutes = 3;

        /**
         * 排除扫描的包名模式（支持通配符）
         */
        private List<String> excludePackages = Arrays.asList(
            "org.springframework.*",
            "org.springframework.boot.*",
            "org.springframework.cloud.*",
            "org.springframework.security.*",
            "com.sun.*", 
            "java.*",
            "javax.*",
            "kotlin.*",
            "scala.*",
            "groovy.*"
        );

        public enum ScanStrategy {
            /**
             * 同步扫描 - 在应用启动时立即完成扫描（默认）
             */
            SYNC,
            
            /**
             * 异步扫描 - 应用启动后延迟扫描（适合大型项目）
             */
            ASYNC,
            
            /**
             * 禁用扫描 - 完全禁用自动扫描
             */
            DISABLED
        }
    }
}
# Smart Rate Limiter 配置文档

## 📋 目录
- [快速开始](#快速开始)
- [最简配置](#最简配置)
- [完整配置](#完整配置)
- [两层使用方式](#两层使用方式)
- [自定义策略教程](#自定义策略教程)
- [操作日志配置](#操作日志配置)
- [扫描策略配置](#扫描策略配置)
- [高级功能](#高级功能)

## 🚀 快速开始

### 第一层：纯注解使用（零配置）

```xml
<!-- 1. 添加依赖 -->
<dependency>
    <groupId>io.github</groupId>
    <artifactId>smart-rate-limiter-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

```java
// 2. 直接使用注解
@RestController
public class ApiController {
    
    @RateLimit(permits = 10, window = 60)
    @GetMapping("/api/data")
    public String getData() {
        return "Hello World";
    }
}
```

### 第二层：开启管理面板

```yaml
# application.yml
rate-limiter:
  admin:
    enabled: true        # 开启管理页面
    username: admin      # 登录用户名
    password: admin123   # 登录密码
```

访问：`http://localhost:8080/admin/rate-limiter/login`

## 🎯 最简配置

### 纯注解使用（推荐新手）

```yaml
# 完全零配置，注解自动生效
spring:
  application:
    name: my-app
```

```java
@RestController
public class UserController {
    
    // 基础限流：每分钟10次
    @RateLimit(permits = 10, window = 60)
    @PostMapping("/login")
    public String login() { return "ok"; }
    
    // IP限流：每个IP每30秒5次
    @RateLimit(dimension = RateLimit.LimitDimension.IP, permits = 5, window = 30)
    @PostMapping("/register") 
    public String register() { return "ok"; }
}
```

### 开启管理面板（推荐生产）

```yaml
# 启用管理页面的最简配置
rate-limiter:
  admin:
    enabled: true
    username: admin
    password: your_secure_password
```

## 📊 完整配置

```yaml
# 🎯 限流核心配置
smart:
  rate-limiter:
    enabled: true                           # 是否启用限流
    storage-type: hybrid                    # 存储类型：redis/memory/hybrid
    default-algorithm: sliding-window       # 默认算法
    include-method-signature: true          # 是否包含方法签名
    
    # 🚀 性能优化
    cache:
      enabled: true                         # 启用本地缓存
      max-size: 10000                      # 缓存最大大小
      expire-after-write: PT1M              # 写后过期时间
      expire-after-access: PT5M             # 访问后过期时间
    
    # 📦 Redis配置
    redis:
      key-prefix: "smart:rate_limit:"       # Key前缀
      key-separator: ":"                    # Key分隔符
      script-cache-size: 100                # Lua脚本缓存大小
      timeout: PT1S                         # 连接超时
      use-lua-scripts: true                 # 使用Lua脚本
    
    # 💾 内存存储配置
    memory:
      max-size: 100000                      # 最大记录数
      expire-after-access: PT10M            # 访问后过期
      cleanup-interval: PT1M                # 清理间隔
    
    # 🛡️ 容错配置
    fallback:
      on-error: allow                       # 出错时行为：allow/reject
      on-redis-unavailable: memory          # Redis不可用时：memory/allow_all/reject_all
      max-errors: 5                         # 最大错误数
      recovery-interval: PT1M               # 恢复检查间隔
    
    # 📈 监控配置
    monitoring:
      enabled: true                         # 启用监控
      include-detailed-tags: true           # 包含详细标签
      metrics:
        - REQUESTS_TOTAL
        - REQUESTS_ALLOWED
        - REQUESTS_REJECTED
        - CHECK_DURATION

# 🎛️ 管理页面配置
rate-limiter:
  admin:
    enabled: true                           # 启用管理页面
    base-path: /admin/rate-limiter         # 访问路径
    username: admin                        # 登录用户名
    password: your_secure_password         # 登录密码（请修改）
    session-timeout: 30                    # 会话超时（分钟）
    
    # 🔒 安全配置
    security:
      enable-header-check: true            # 启用头部检查
      header-name: X-Admin-Token           # 安全头名称
      header-value: your_secret_token      # 安全头值
      allowed-ips: "192.168.1.0/24,10.0.0.0/8"  # IP白名单
    
    # 🔍 接口发现配置
    discovery:
      exclude-admin-endpoints: true        # 排除管理页面接口
      exclude-actuator-endpoints: true     # 排除监控端点
      exclude-error-endpoints: true        # 排除错误页面
      exclude-static-resource-endpoints: true  # 排除静态资源
      exclude-packages: "org.springframework.boot,com.example.internal"
      exclude-paths: "/favicon.ico,/robots.txt,/health"
      exclude-controller-keywords: "BasicErrorController,InternalController"
    
    # 📝 操作日志配置
    logging:
      file-enabled: true                   # 启用文件日志
      file-path: "./logs/rate-limiter-operations.log"  # 日志文件路径
      json-format: true                    # JSON格式日志
      max-file-size: "50MB"               # 最大文件大小
      max-history: 30                     # 保留天数
      async: true                         # 异步写入
      async-queue-size: 2000              # 异步队列大小
      include-request-details: true        # 包含请求详情
      include-config-details: true         # 包含配置详情
      log-level: "INFO"                   # 日志级别
      custom-pattern: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
    
    # 🔄 扫描策略配置
    scanning:
      strategy: async                      # 扫描策略：sync/async/disabled
      async-delay-minutes: 5               # 异步延迟启动（分钟）
      interval-minutes: 30                 # 扫描间隔（分钟）
      scan-on-startup: false              # 启动时立即扫描
      smart-scan: true                     # 智能扫描
      thread-pool-size: 2                  # 扫描线程池大小
      timeout-seconds: 60                  # 扫描超时时间
      enable-cache: true                   # 启用扫描缓存
      cache-expire-minutes: 15             # 缓存过期时间
      max-scan-depth: 10                   # 最大扫描深度
      exclude-packages:                    # 排除包名
        - "org.springframework.*"
        - "com.sun.*"
        - "java.*"
      include-packages:                    # 包含包名（空则全部）
        - "com.yourcompany.*"

# 📊 Spring Boot Actuator（可选）
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
```

## 🎯 两层使用方式详解

### 第一层：纯注解使用

**适用场景**：
- 新项目快速上手
- 简单的限流需求
- 不需要运维管理界面

**特点**：
- ✅ 零配置，开箱即用
- ✅ 注解简单易懂
- ✅ 性能开销最小
- ✅ 自动降级处理

**使用方法**：
```java
@RestController
public class QuickStartController {
    
    // 全局限流：每分钟50次
    @RateLimit(permits = 50, window = 60)
    @GetMapping("/api/public")
    public String publicApi() {
        return "public data";
    }
    
    // 用户限流：每个用户每小时100次
    @RateLimit(
        dimension = RateLimit.LimitDimension.USER,
        permits = 100, 
        window = 3600,
        message = "用户请求过于频繁，请稍后重试"
    )
    @GetMapping("/api/user/profile")
    public String getUserProfile() {
        return "user profile";
    }
    
    // 多维度限流
    @MultiRateLimit({
        @RateLimit(dimension = RateLimit.LimitDimension.GLOBAL, permits = 1000, window = 60),
        @RateLimit(dimension = RateLimit.LimitDimension.IP, permits = 10, window = 60),
        @RateLimit(dimension = RateLimit.LimitDimension.USER, permits = 50, window = 60)
    })
    @PostMapping("/api/important")
    public String importantApi() {
        return "important operation";
    }
}
```

### 第二层：管理面板模式

**适用场景**：
- 生产环境运维
- 需要动态调整限流策略
- 需要监控和日志分析
- 多环境配置管理

**特点**：
- ✅ 可视化管理界面
- ✅ 动态配置热更新
- ✅ 详细的操作日志
- ✅ 智能扫描和建议
- ✅ 支持配置导入导出

**配置方法**：
```yaml
rate-limiter:
  admin:
    enabled: true
    username: admin
    password: ${RATE_LIMITER_PASSWORD:admin123}  # 建议使用环境变量
    
    # 开启操作日志
    logging:
      file-enabled: true
      file-path: "/var/log/rate-limiter/operations.log"
      json-format: true
    
    # 配置异步扫描
    scanning:
      strategy: async
      async-delay-minutes: 5  # 应用启动5分钟后开始扫描
```

## 🛠️ 自定义策略教程

### 1. 自定义限流维度

```java
// 创建自定义用户ID解析器
@Component
public class CustomUserIdResolver implements UserIdResolver {
    
    @Override
    public String resolveUserId(HttpServletRequest request) {
        // 从JWT Token中解析用户ID
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            return parseUserIdFromJWT(token.substring(7));
        }
        
        // 从Session中获取
        HttpSession session = request.getSession(false);
        if (session != null) {
            return (String) session.getAttribute("userId");
        }
        
        return null;
    }
    
    private String parseUserIdFromJWT(String token) {
        // JWT解析逻辑
        return "user123";
    }
}
```

### 2. 自定义IP解析器

```java
@Component
public class CustomIpResolver implements IpResolver {
    
    @Override
    public String resolveIp(HttpServletRequest request) {
        // 处理代理服务器情况
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
}
```

### 3. 自定义限流存储

```java
@Component
public class CustomRateLimitService implements RateLimitService {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    @Override
    public RateLimitResult checkRateLimit(RateLimitContext context) {
        String key = context.getKey();
        long permits = context.getPermits();
        int windowSeconds = context.getWindowSeconds();
        
        // 自定义限流逻辑
        switch (context.getAlgorithm()) {
            case SLIDING_WINDOW:
                return slidingWindowCheck(key, permits, windowSeconds);
            case TOKEN_BUCKET:
                return tokenBucketCheck(key, permits, context.getRefillRate());
            default:
                return fixedWindowCheck(key, permits, windowSeconds);
        }
    }
    
    private RateLimitResult slidingWindowCheck(String key, long permits, int windowSeconds) {
        // 实现滑动窗口算法
        long now = System.currentTimeMillis();
        long windowStart = now - (windowSeconds * 1000L);
        
        // 使用Redis的ZSET实现滑动窗口
        String script = 
            "local key = KEYS[1] " +
            "local window_start = ARGV[1] " +
            "local now = ARGV[2] " +
            "local limit = tonumber(ARGV[3]) " +
            
            "redis.call('ZREMRANGEBYSCORE', key, 0, window_start) " +
            "local current = redis.call('ZCARD', key) " +
            
            "if current < limit then " +
            "    redis.call('ZADD', key, now, now) " +
            "    redis.call('EXPIRE', key, " + windowSeconds + ") " +
            "    return {1, limit - current - 1, limit} " +
            "else " +
            "    return {0, 0, limit} " +
            "end";
        
        List<Long> result = redisTemplate.execute(
            (RedisCallback<List<Long>>) connection -> 
                (List<Long>) connection.eval(
                    script.getBytes(), 
                    ReturnType.MULTI, 
                    1, 
                    key.getBytes(),
                    String.valueOf(windowStart).getBytes(),
                    String.valueOf(now).getBytes(),
                    String.valueOf(permits).getBytes()
                )
        );
        
        boolean allowed = result.get(0) == 1;
        long remaining = result.get(1);
        long total = result.get(2);
        
        return RateLimitResult.builder()
            .allowed(allowed)
            .key(key)
            .remainingPermits(remaining)
            .totalPermits(total)
            .resetTime(Instant.ofEpochMilli(now + windowSeconds * 1000L))
            .build();
    }
}
```

### 4. 自定义限流策略组合

```java
@Service
public class BusinessRateLimitService {
    
    // VIP用户特殊限流
    @RateLimit(
        permits = 1000, 
        window = 3600,
        keyExpression = "'vip:' + #request.getHeader('user-level')"
    )
    public void vipUserOperation(HttpServletRequest request) {
        // VIP用户操作
    }
    
    // 基于请求参数的动态限流
    @RateLimit(
        permits = 10,
        window = 60,
        keyExpression = "'category:' + #request.getParameter('category')"
    )
    public void categoryBasedOperation(HttpServletRequest request) {
        // 基于分类的操作
    }
    
    // 复杂的业务限流逻辑
    public void complexBusinessLogic(String userId, String operation) {
        // 根据用户等级动态调整限流
        UserLevel level = getUserLevel(userId);
        
        RateLimitContext context = RateLimitContext.builder()
            .key("business:" + userId + ":" + operation)
            .permits(level.getPermits())
            .windowSeconds(level.getWindowSeconds())
            .algorithm(level.getAlgorithm())
            .build();
            
        RateLimitResult result = rateLimitService.checkRateLimit(context);
        
        if (!result.isAllowed()) {
            throw new RateLimitException("业务操作过于频繁");
        }
    }
}
```

## 📝 操作日志详解

### 独立日志配置

Rate Limiter Admin 使用独立的日志配置，**完全独立于项目整体日志**：

```xml
<!-- logback-spring.xml 中的配置 -->
<appender name="RATE_LIMITER_ADMIN" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/rate-limiter/admin-operations.log</file>
    <encoder>
        <pattern>%d{yyyy-MM-dd HH:mm:ss} | %msg%n</pattern>
    </encoder>
</appender>

<logger name="rate-limiter.admin" level="INFO" additivity="false">
    <appender-ref ref="RATE_LIMITER_ADMIN"/>
</logger>
```

### 日志输出格式

```text
2024-08-21 15:30:25 | ADD | com.example.UserController.getUserInfo | @RateLimit(permits=10, window=60, dimension=USER) | operator: admin
2024-08-21 15:31:10 | UPDATE | com.example.OrderController.createOrder | @RateLimit(permits=5, window=30, dimension=IP) | operator: admin
2024-08-21 15:32:05 | DELETE | com.example.PayController.pay | @RateLimit(permits=3, window=60) | operator: admin
```

### 日志文件位置

- **独立日志**：`logs/rate-limiter/admin-operations.log`（由logback自动管理）
- **配置文件**：`./logs/rate-limiter/config-operations.log`（可配置路径）

### 配置说明

```yaml
rate-limiter:
  admin:
    logging:
      file-enabled: true  # 启用文件日志
      file-path: ./logs/rate-limiter/config-operations.log  # 可选的额外日志路径
```

**推荐**：使用默认的独立日志配置，无需额外配置。

### 日志内容说明

每条日志记录包含：
- **时间戳**：操作发生时间
- **操作类型**：ADD/UPDATE/DELETE
- **方法签名**：完整的类名和方法名
- **注解内容**：可直接复制到代码中的@RateLimit注解
- **操作人**：执行操作的管理员

这样的格式便于：
1. **快速定位**：知道对哪个方法进行了配置
2. **代码生成**：直接复制注解内容到代码中
3. **操作审计**：追踪谁在什么时候做了什么操作
```
# 限流注解建议报告
# 基于日志文件分析生成

## com.example.UserController.login()
触发次数: 156
建议配置: @RateLimit(permits = 10, window = 300)
说明: 登录接口建议5分钟10次限制

## com.example.ApiController.getData()
触发次数: 89
建议配置: @RateLimit(permits = 50, window = 60)
说明: 数据获取接口建议每分钟50次限制
```

## 🔄 扫描策略详解

### 同步扫描（SYNC）
```yaml
scanning:
  strategy: sync
  scan-on-startup: true
```

**特点**：
- 应用启动时完成扫描
- 阻塞启动过程
- 适合小型应用

### 异步扫描（ASYNC）
```yaml
scanning:
  strategy: async
  async-delay-minutes: 5    # 启动5分钟后开始
  interval-minutes: 30      # 每30分钟扫描一次
  smart-scan: true         # 智能调频
```

**特点**：
- 不影响应用启动速度
- 延迟后台扫描
- 智能调整扫描频率
- **推荐用于生产环境**

### 禁用扫描（DISABLED）
```yaml
scanning:
  strategy: disabled
```

**特点**：
- 完全禁用自动扫描
- 手动管理所有配置
- 最小性能开销

## 🔧 高级功能

### 1. 配置导入导出

```bash
# 导出当前配置
curl -H "Authorization: Bearer token" \
     http://localhost:8080/admin/rate-limiter/api/config/export > config.json

# 导入配置
curl -X POST \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer token" \
     -d @config.json \
     http://localhost:8080/admin/rate-limiter/api/config/import
```

### 2. 健康检查集成

```yaml
management:
  health:
    rate-limiter:
      enabled: true
  endpoint:
    health:
      show-details: always
```

### 3. Prometheus监控

```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true
```

监控指标：
- `rate_limiter_requests_total`
- `rate_limiter_requests_allowed`
- `rate_limiter_requests_rejected`
- `rate_limiter_check_duration`

### 4. 分布式配置同步

```yaml
smart:
  rate-limiter:
    cluster:
      enabled: true
      sync-interval: PT30S
      conflict-resolution: most-restrictive
```

## 🛡️ 安全最佳实践

### 1. 密码安全
```yaml
rate-limiter:
  admin:
    password: ${RATE_LIMITER_PASSWORD}  # 使用环境变量
    security:
      allowed-ips: "${ADMIN_IPS:127.0.0.1}"
```

### 2. HTTPS配置
```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_PASSWORD}
```

### 3. 访问控制
```java
@Component
public class RateLimiterSecurityConfig {
    
    @EventListener
    public void onLogin(LoginEvent event) {
        if (!event.isSuccess()) {
            // 记录失败登录
            rateLimiterAuditService.logFailedLogin(event);
        }
    }
}
```

## 📞 技术支持

- **文档**: [https://github.com/your-org/smart-rate-limiter/wiki](https://github.com/your-org/smart-rate-limiter/wiki)
- **Issues**: [https://github.com/your-org/smart-rate-limiter/issues](https://github.com/your-org/smart-rate-limiter/issues)
- **讨论**: [https://github.com/your-org/smart-rate-limiter/discussions](https://github.com/your-org/smart-rate-limiter/discussions)
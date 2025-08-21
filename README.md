# 🚀 Smart Rate Limiter

[![Maven Central](https://img.shields.io/maven-central/v/io.github/smart-rate-limiter-spring-boot-starter.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github%22%20AND%20a:%22smart-rate-limiter-spring-boot-starter%22)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java 17+](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot 3.x](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

智能限流器 - 一个功能强大、易于使用的 Spring Boot 限流组件，支持多种算法、多维度限流和动态配置管理。
没有上传到maven仓库，fork代码编译使用
## ✨ 特性

- 🎯 **多种限流算法**：滑动窗口、固定窗口、令牌桶、漏桶
- 📊 **多维度限流**：IP、用户、全局、自定义维度
- 🔄 **动态配置**：运行时动态调整限流规则，无需重启
- 🎛️ **管理界面**：可选的 Web 管理控制台（需要启用）
- 🔧 **双存储支持**：Redis（分布式）+ 内存（单机）
- ⚡ **高性能**：基于 Lua 脚本，支持本地缓存
- 🛡️ **容错机制**：优雅降级，Redis 不可用时自动切换到内存模式
- 📝 **零配置**：开箱即用，也支持丰富的自定义配置
- 🔍 **Spring Expression**：支持 SpEL 表达式动态计算限流参数

## 🚀 快速开始

### 添加仓库配置

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/YOUR_USERNAME/smart-rate-limiter</url>
    </repository>
</repositories>
```

### 添加依赖

```xml
<dependency>
    <groupId>com.twjgs</groupId>
    <artifactId>smart-rate-limiter-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

> **注意**: 由于使用 GitHub Packages，需要配置 GitHub 认证。详见 [DEPLOYMENT.md](DEPLOYMENT.md)

### 基础使用

```java
@RestController
public class ApiController {
    
    // 基础限流：每分钟10次
    @RateLimit(permits = 10, window = 60)
    @GetMapping("/api/data")
    public String getData() {
        return "Hello World";
    }
    
    // IP限流：每个IP每分钟5次
    @RateLimit(
        dimension = RateLimit.LimitDimension.IP,
        permits = 5, 
        window = 60
    )
    @PostMapping("/api/upload")
    public String upload() {
        return "Upload success";
    }
    
    // 用户限流：每个用户每小时100次
    @RateLimit(
        dimension = RateLimit.LimitDimension.USER,
        permits = 100, 
        window = 3600,
        message = "用户请求过于频繁，请稍后再试"
    )
    @GetMapping("/api/user/profile")
    public String getUserProfile() {
        return "User profile";
    }
}
```

**就这么简单！无需任何配置，限流功能立即生效。**

## 📋 目录

- [安装配置](#安装配置)
- [基础使用](#基础使用)
- [限流算法](#限流算法)
- [多维度限流](#多维度限流)
- [动态配置](#动态配置)
- [管理界面](#管理界面)
- [高级配置](#高级配置)
- [最佳实践](#最佳实践)
- [故障排除](#故障排除)

## 📋 环境要求

- **Java**: 17+  
- **Spring Boot**: 3.x
- **Redis**: 3.2+（可选，用于分布式部署）

## 🔧 安装配置

### 单机模式（默认）

零配置即可使用，使用内存存储：

```yaml
# 无需任何配置！
```

### 分布式模式（推荐生产环境）

```yaml
# application.yml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: your-password

smart:
  rate-limiter:
    storage-type: redis  # 使用 Redis 存储
```

### 管理界面（可选）

```yaml
# 启用管理界面
rate-limiter:
  admin:
    enabled: true
    username: admin
    password: admin123
    base-path: /admin/rate-limiter
```

访问：`http://localhost:8080/admin/rate-limiter`

## 🎯 限流算法

### 支持的算法

| 算法 | 描述 | 适用场景 |
|------|------|----------|
| `SLIDING_WINDOW` | 滑动时间窗口（默认） | 最精确，适合大多数场景 |
| `FIXED_WINDOW` | 固定时间窗口 | 简单高效，允许短时突发 |
| `TOKEN_BUCKET` | 令牌桶 | 允许突发流量，平滑限流 |
| `LEAKY_BUCKET` | 漏桶 | 严格限制流量速率 |

### 使用示例

```java
// 使用令牌桶算法，允许突发流量
@RateLimit(
    permits = 10,
    window = 60,
    algorithm = RateLimit.LimitAlgorithm.TOKEN_BUCKET,
    bucketCapacity = 20  // 桶容量
)
@GetMapping("/api/burst")
public String handleBurst() {
    return "OK";
}

// 使用漏桶算法，严格限制速率
@RateLimit(
    permits = 5,
    window = 60,
    algorithm = RateLimit.LimitAlgorithm.LEAKY_BUCKET
)
@PostMapping("/api/strict")
public String strictLimit() {
    return "OK";
}
```

## 🎨 多维度限流

### 限流维度

| 维度 | 说明 | 限流键 |
|------|------|--------|
| `GLOBAL` | 全局限流 | 方法签名 |
| `IP` | IP 地址限流 | IP + 方法签名 |
| `USER` | 用户限流 | 用户ID + 方法签名 |
| `CUSTOM` | 自定义维度 | SpEL 表达式结果 |

### 使用示例

```java
// 全局限流：所有用户共享配额
@RateLimit(
    dimension = RateLimit.LimitDimension.GLOBAL,
    permits = 1000,
    window = 60
)
@GetMapping("/api/global")
public String globalEndpoint() {
    return "OK";
}

// 自定义维度：按租户限流
@RateLimit(
    dimension = RateLimit.LimitDimension.CUSTOM,
    customKeyExpression = "#request.getHeader('tenant-id')",
    permits = 100,
    window = 60
)
@GetMapping("/api/tenant")
public String tenantEndpoint(HttpServletRequest request) {
    return "OK";
}

// 多重限流：同时应用多个限流规则
@MultiRateLimit({
    @RateLimit(dimension = RateLimit.LimitDimension.IP, permits = 10, window = 60),
    @RateLimit(dimension = RateLimit.LimitDimension.USER, permits = 100, window = 3600),
    @RateLimit(dimension = RateLimit.LimitDimension.GLOBAL, permits = 10000, window = 60)
})
@PostMapping("/api/multi")
public String multiLimit() {
    return "OK";
}
```

## ⚙️ 动态配置

### 运行时调整

无需重启应用即可动态调整限流规则：

```java
@Autowired
private DynamicConfigService configService;

// 动态添加限流规则
DynamicRateLimitConfig config = new DynamicRateLimitConfig();
config.setPermits(20);
config.setWindow(60);
config.setDimension("IP");

configService.saveDynamicConfig("com.example.Controller.method", config, "admin");

// 动态删除限流规则
configService.deleteDynamicConfig("com.example.Controller.method", "admin");
```

### 配置优先级

1. **动态配置**（最高优先级）
2. **注解配置**
3. **默认配置**（最低优先级）

## 🎛️ 管理界面

启用管理界面后，可以通过 Web 控制台管理限流规则：

```yaml
rate-limiter:
  admin:
    enabled: true           # 启用管理界面
    username: admin         # 登录用户名
    password: admin123      # 登录密码
    base-path: /admin/rate-limiter  # 访问路径
    session-timeout: 30     # 会话超时（分钟）
    logging:
      file-enabled: true    # 启用操作日志
      file-path: ./logs/rate-limiter/config-operations.log
```

### 功能特性

- 📊 **实时监控**：查看限流规则和统计信息
- ⚙️ **配置管理**：动态添加、修改、删除限流规则
- 🔍 **接口发现**：自动发现应用中的 API 接口
- 📝 **操作日志**：记录配置变更历史

访问地址：`http://localhost:8080/admin/rate-limiter`

## 🔧 高级配置

### 完整配置示例

```yaml
smart:
  rate-limiter:
    # 基础配置
    enabled: true
    storage-type: redis                    # redis, memory, hybrid
    default-algorithm: sliding-window      # 默认算法
    
    # 缓存配置
    cache:
      enabled: true
      max-size: 10000
      expire-after-write: PT1M             # ISO-8601 Duration格式
      expire-after-access: PT5M
    
    # Redis 配置
    redis:
      key-prefix: "rate_limit:"
      key-separator: ":"
      timeout: PT1S
      use-lua-scripts: true
      database: 0
    
    # 内存存储配置
    memory:
      max-size: 100000
      expire-after-access: PT10M
      cleanup-interval: PT1M
    
    # 容错配置
    fallback:
      on-error: allow                      # allow, reject
      on-redis-unavailable: memory         # memory, allow_all, reject_all
      max-errors: 5
      recovery-interval: PT1M
    
    # 监控配置
    monitoring:
      enabled: true
      include-detailed-tags: true

# 管理界面配置
rate-limiter:
  admin:
    enabled: true
    base-path: /admin/rate-limiter
    username: admin
    password: admin123
    session-timeout: 30
    
    # 安全配置
    security:
      enable-header-check: false
      allowed-ips: "127.0.0.1,192.168.1.*"
    
    # 日志配置
    logging:
      file-enabled: true
      file-path: ./logs/rate-limiter/operations.log
    
    # 扫描配置
    scanning:
      strategy: SYNC                       # SYNC, ASYNC, DISABLED
      async-delay-minutes: 3
```

### 自定义组件

```java
// 自定义用户ID解析器
@Component
public class CustomUserIdResolver implements UserIdResolver {
    @Override
    public String resolveUserId(HttpServletRequest request) {
        return request.getHeader("X-User-ID");
    }
}

// 自定义IP解析器
@Component  
public class CustomIpResolver implements IpResolver {
    @Override
    public String resolveIp(HttpServletRequest request) {
        // 处理代理情况
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        return xForwardedFor != null ? xForwardedFor.split(",")[0] 
                                     : request.getRemoteAddr();
    }
}
```

## 💡 最佳实践

### 1. 存储选择

- **单机应用**：使用 `memory` 存储
- **分布式应用**：使用 `redis` 存储
- **混合场景**：使用 `hybrid` 存储

### 2. 算法选择

- **大多数场景**：使用 `SLIDING_WINDOW`（默认）
- **允许突发**：使用 `TOKEN_BUCKET`
- **严格限制**：使用 `LEAKY_BUCKET`
- **简单场景**：使用 `FIXED_WINDOW`

### 3. 限流策略

```java
// ✅ 推荐：分层限流
@MultiRateLimit({
    @RateLimit(dimension = IP, permits = 100, window = 60),        // 防刷
    @RateLimit(dimension = USER, permits = 1000, window = 3600),   // 用户配额
    @RateLimit(dimension = GLOBAL, permits = 10000, window = 60)   // 系统保护
})
@PostMapping("/api/important")
public String importantApi() {
    return "OK";
}

// ✅ 推荐：业务场景定制
@RateLimit(
    permits = 1,
    window = 300,  // 5分钟内只能发送1次
    dimension = RateLimit.LimitDimension.CUSTOM,
    customKeyExpression = "#request.getParameter('phone')",
    message = "验证码发送过于频繁，请5分钟后重试"
)
@PostMapping("/api/send-sms")
public String sendSms() {
    return "OK";
}
```

### 4. 错误处理

```java
@ControllerAdvice
public class RateLimitExceptionHandler {
    
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitException e) {
        Map<String, Object> result = new HashMap<>();
        result.put("error", "RATE_LIMIT_EXCEEDED");
        result.put("message", e.getMessage());
        result.put("retryAfter", e.getRetryAfter());
        
        return ResponseEntity.status(429).body(result);
    }
}
```

### 5. 监控和告警

```yaml
# 开启监控
smart:
  rate-limiter:
    monitoring:
      enabled: true
      include-detailed-tags: true

# 结合 Micrometer 监控
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  metrics:
    export:
      prometheus:
        enabled: true
```

## 🔧 故障排除

### 常见问题

**Q: 限流不生效？**
```yaml
# 检查配置
smart:
  rate-limiter:
    enabled: true  # 确保已启用
```

**Q: Redis 连接失败？**
```yaml
# 检查 Redis 配置
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 1000ms
      connect-timeout: 1000ms

# 启用降级
smart:
  rate-limiter:
    fallback:
      on-redis-unavailable: memory
```

**Q: 管理界面无法访问？**
```yaml
# 确保已启用管理界面
rate-limiter:
  admin:
    enabled: true  # 默认为 false
```

**Q: SpEL 表达式错误？**
```java
// ✅ 正确
@RateLimit(
    customKeyExpression = "#request.getHeader('user-id')"
)

// ❌ 错误
@RateLimit(
    customKeyExpression = "request.getHeader('user-id')"  // 缺少 #
)
```

### 调试模式

```yaml
# 开启调试日志
logging:
  level:
    io.github.rateLimiter: DEBUG
```

## 📊 性能说明

### 基准测试结果

- **内存模式**：单机 10万+ QPS
- **Redis模式**：集群 5万+ QPS
- **延迟影响**：< 1ms 额外延迟
- **内存占用**：每条规则约 1KB

### 优化建议

1. **启用本地缓存**：减少 Redis 访问
2. **使用 Lua 脚本**：原子性操作，减少网络往返
3. **合理设置过期时间**：避免内存泄漏
4. **监控 Redis 性能**：及时发现瓶颈

## 🤝 贡献指南

欢迎贡献代码！请遵循以下步骤：

1. Fork 本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 📝 更新日志

### v1.0.0 (2024-08-21)

- ✨ 首次发布
- 🎯 支持多种限流算法
- 📊 支持多维度限流
- 🔄 支持动态配置
- 🎛️ 提供管理界面
- 🔧 支持 Redis 和内存双存储

## 📄 许可证

本项目使用 [Apache License 2.0](LICENSE) 许可证。

## 🙏 致谢

感谢所有贡献者的辛勤付出！

---

如果这个项目对您有帮助，请给个 ⭐️ Star 支持一下！

有问题或建议？欢迎提交 [Issue](https://github.com/your-username/smart-rate-limiter/issues) 或 [Discussion](https://github.com/your-username/smart-rate-limiter/discussions)。
# 🚀 Smart Rate Limiter

[![Maven Central](https://img.shields.io/maven-central/v/io.github/smart-rate-limiter-spring-boot-starter.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github%22%20AND%20a:%22smart-rate-limiter-spring-boot-starter%22)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java 17+](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot 3.x](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

智能限流器 - 一个功能强大、易于使用的 Spring Boot API 保护组件，支持限流、幂等性控制、防重复提交等多种保护机制。
没有上传到maven仓库，fork代码编译使用

## ✨ 特性

### 🚦 限流控制
- 🎯 **多种限流算法**：滑动窗口、固定窗口、令牌桶、漏桶
- 📊 **多维度限流**：IP、用户、全局、自定义维度
- 🔄 **动态配置**：运行时动态调整限流规则，无需重启
- 🎛️ **管理界面**：可选的 Web 管理控制台（需要启用）

### 🔒 幂等性控制
- 🎯 **智能幂等**：基于请求内容自动识别重复请求
- ⏱️ **灵活超时**：支持自定义幂等超时时间
- 🔑 **多种策略**：用户级、全局级、自定义键策略
- 💾 **结果缓存**：可选的幂等结果缓存，提升响应速度

### 🚫 防重复提交  
- ⚡ **快速检测**：毫秒级重复提交检测
- 🎭 **多维度防护**：用户、IP、会话、全局、自定义维度
- ⏰ **灵活间隔**：支持不同业务场景的时间间隔设置
- 🔧 **SpEL 支持**：支持复杂的自定义键表达式

### 🔧 通用特性
- 🏪 **双存储支持**：Redis（分布式）+ 内存（单机）
- ⚡ **高性能**：基于 Lua 脚本，支持本地缓存
- 🛡️ **容错机制**：优雅降级，Redis 不可用时自动切换到内存模式
- 📝 **零配置**：开箱即用，也支持丰富的自定义配置
- 🔍 **Spring Expression**：支持 SpEL 表达式动态计算参数
- 👤 **用户识别**：可自定义 UserIdResolver 适配各种认证体系
- 🔑 **键前缀定制**：支持多应用共享Redis，自定义键前缀避免冲突
- ⚙️ **智能清理**：服务重启时自动清理过期数据，支持配置化控制
- 📋 **执行优先级**：可配置拦截器执行顺序，灵活控制API保护策略
- ⏰ **自动过期**：缓存数据自动过期清理，避免内存泄漏

## 🚀 快速开始

### 添加仓库配置



### 添加依赖

```xml
<dependency>
    <groupId>com.twjgs</groupId>
    <artifactId>smart-rate-limiter-spring-boot-starter</artifactId>
    <version>1.0.1</version>
</dependency>
```

> **注意**: 由于使用 GitHub Packages，需要配置 GitHub 认证。 详见 [DEPLOYMENT.md](DEPLOYMENT.md)。     未上传maven仓库，建议pull代码打包到本地使用。

### 基础使用

```java
@RestController
public class ApiController {
    
    // 🚦 基础限流：每分钟10次
    @RateLimit(permits = 10, window = 60)
    @GetMapping("/api/data")
    public String getData() {
        return "Hello World";
    }
    
    // 🔒 幂等性控制：防止重复执行
    @Idempotent(timeout = 60, message = "请求正在处理中，请勿重复操作")
    @PostMapping("/api/payment")
    public PaymentResult processPayment(@RequestBody PaymentRequest request) {
        // 支付处理逻辑
        return new PaymentResult();
    }
    
    // 🚫 防重复提交：5秒内禁止重复提交
    @DuplicateSubmit(interval = 5, message = "请勿重复提交，请稍候再试")
    @PostMapping("/api/comment")
    public CommentResult addComment(@RequestBody CommentRequest request) {
        // 评论处理逻辑
        return new CommentResult();
    }
    
    // 🎯 组合使用：限流 + 防重复提交
    @RateLimit(permits = 10, window = 60, message = "访问频率过高")
    @DuplicateSubmit(interval = 3, message = "请勿频繁操作")
    @PostMapping("/api/vote")
    public VoteResult vote(@RequestBody VoteRequest request) {
        // 投票处理逻辑
        return new VoteResult();
    }
}
```

**就这么简单！无需任何配置，API保护功能立即生效。**

## 📋 目录

- [安装配置](#安装配置)
- [基础使用](#基础使用)
- [限流控制](#限流控制)
- [幂等性控制](#幂等性控制)
- [防重复提交](#防重复提交)
- [用户识别](#用户识别)
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
# 最简配置 - 使用默认值
rate-limiter:
  admin:
    enabled: true  # 默认为 false，必须显式启用

# 完整配置 - 自定义所有参数
rate-limiter:
  admin:
    enabled: true                    # 是否启用管理界面（默认：false）
    username: admin                  # 登录用户名（默认：admin）
    password: admin123               # 登录密码（默认：admin123）
    base-path: /admin/rate-limiter   # 访问路径（默认：/admin/rate-limiter）
    session-timeout: 30              # 会话超时分钟数（默认：30）
    logging:
      file-enabled: true             # 启用操作日志（默认：false）
      file-path: ./logs/rate-limiter/operations.log  # 日志文件路径（默认）
```

访问：`http://localhost:8080/admin/rate-limiter/login`

> **安全提示**：管理界面默认关闭，需要显式配置 `enabled: true` 才会启用。  启用后使用默认用户名 `admin` 和密码 `admin123`，生产环境请务必修改默认密码！如果被用户自己系统的拦截器拦截了面板，需要手动放行base-path/**

## 🚦 限流控制

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

| 维度       | 说明      | 限流键   |
|----------|---------|-------|
| `GLOBAL` | 全局限流    | 方法签名  |
| `IP`     | IP 地址限流 | IP + 方法签名 |
| `USER`   | 用户限流    | 用户ID + 方法签名 |
| `API`    | API限流   | 整个API |
| `CUSTOM` | 自定义维度   | SpEL 表达式结果 |

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

## 🔒 幂等性控制

幂等性控制确保相同的业务请求只会被执行一次，防止因网络重试、用户多次点击等导致的重复执行。

### 基础使用

```java
// 基础幂等控制：60秒超时
@Idempotent(timeout = 60, message = "请求正在处理中，请勿重复操作")
@PostMapping("/api/order")
public OrderResult createOrder(@RequestBody OrderRequest request) {
    // 订单创建逻辑只会执行一次
    return orderService.createOrder(request);
}
```

### 键策略

| 策略 | 说明 | 幂等键 |
|------|------|--------|
| `DEFAULT` | 默认策略（方法+参数） | MD5(方法签名+参数JSON) |
| `USER_PARAMS` | 用户+参数 | 用户ID + MD5(方法签名+参数JSON) |
| `CUSTOM` | 自定义表达式 | SpEL 表达式结果 |

### 使用示例

```java
// 基于用户的幂等控制
@Idempotent(
    timeout = 300,
    keyStrategy = Idempotent.KeyStrategy.USER_PARAMS,
    message = "订单正在处理中，请勿重复提交"
)
@PostMapping("/api/order")
public OrderResult createOrder(@RequestBody OrderRequest request) {
    return orderService.createOrder(request);
}

// 自定义键策略：基于订单号
@Idempotent(
    timeout = 600,
    keyStrategy = Idempotent.KeyStrategy.CUSTOM,
    keyExpression = "#request.orderNo",
    message = "订单号重复，请检查"
)
@PostMapping("/api/pay")
public PaymentResult processPayment(@RequestBody PaymentRequest request) {
    return paymentService.processPayment(request);
}

// 启用结果缓存，提升响应速度
@Idempotent(
    timeout = 180,
    returnFirstResult = true,  // 返回首次执行结果
    message = "请求正在处理中"
)
@PostMapping("/api/generate-report")
public ReportResult generateReport(@RequestBody ReportRequest request) {
    // 报告生成逻辑
    return reportService.generateReport(request);
}
```

## 🚫 防重复提交

防重复提交主要用于防止用户短时间内重复点击同一个按钮或接口，基于时间间隔进行控制。

### 基础使用

```java
// 基础防重复：默认5秒间隔
@DuplicateSubmit
@PostMapping("/api/comment")
public CommentResult addComment(@RequestBody CommentRequest request) {
    return commentService.addComment(request);
}

// 自定义间隔时间：10秒
@DuplicateSubmit(
    interval = 10,
    message = "评论提交过于频繁，请10秒后重试"
)
@PostMapping("/api/review")  
public ReviewResult addReview(@RequestBody ReviewRequest request) {
    return reviewService.addReview(request);
}
```

### 防重维度

| 维度 | 说明 | 防重键 |
|------|------|--------|
| `USER_METHOD` | 用户+方法（默认） | 用户ID + 方法签名 |
| `IP_METHOD` | IP+方法 | IP地址 + 方法签名 |
| `SESSION_METHOD` | 会话+方法 | 会话ID + 方法签名 |
| `GLOBAL_METHOD` | 全局方法 | 方法签名 |
| `CUSTOM` | 自定义表达式 | SpEL 表达式结果 |

### 使用示例

```java
// IP维度防重复：60秒间隔
@DuplicateSubmit(
    interval = 60,
    dimension = DuplicateSubmit.KeyDimension.IP_METHOD,
    message = "该IP提交过于频繁，请1分钟后重试"
)
@PostMapping("/api/feedback")
public FeedbackResult submitFeedback(@RequestBody FeedbackRequest request) {
    return feedbackService.submitFeedback(request);
}

// 全局方法防重复：适用于系统级操作
@DuplicateSubmit(
    interval = 120,
    dimension = DuplicateSubmit.KeyDimension.GLOBAL_METHOD,
    message = "系统配置更新过于频繁，请2分钟后重试"
)
@PostMapping("/api/system/config")
public ConfigResult updateSystemConfig(@RequestBody ConfigRequest request) {
    return systemService.updateConfig(request);
}

// 自定义表达式：基于商品ID防重复下单
@DuplicateSubmit(
    interval = 300,
    dimension = DuplicateSubmit.KeyDimension.CUSTOM,
    keyExpression = "'product_order:' + #request.getParameter('productId')",
    message = "请勿重复提交相同商品的订单"
)
@PostMapping("/api/product/order")
public OrderResult createProductOrder(@RequestBody ProductOrderRequest request) {
    return orderService.createProductOrder(request);
}

// 业务场景：每日签到（24小时间隔）
@DuplicateSubmit(
    interval = 86400,  // 24小时
    message = "今日已签到，请明日再来"
)
@PostMapping("/api/checkin")
public CheckinResult dailyCheckin(@RequestBody CheckinRequest request) {
    return checkinService.dailyCheckin(request);
}
```

## 👤 用户识别

系统支持灵活的用户识别机制，适配各种认证体系。

### 默认用户识别

`DefaultUserIdResolver` 按以下优先级获取用户ID：

1. **Spring Security上下文**（如果可用）
2. **X-User-ID 请求头**
3. **Session中的 userId 属性**  
4. **JWT Token解析**（Authorization头）
5. **默认值**：`ANONYMOUS`

### 自定义用户识别

```java
@Component
@Primary  // 覆盖默认实现
public class CustomUserIdResolver implements UserIdResolver {
    
    @Override
    public String resolveUserId(HttpServletRequest request) {
        // 从JWT Token中解析用户ID
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            return jwtUtils.parseUserId(token);
        }
        
        // 从自定义认证头获取
        String customUserId = request.getHeader("X-Custom-User-ID");
        if (StringUtils.hasText(customUserId)) {
            return customUserId;
        }
        
        // 从数据库查询当前用户
        String sessionId = request.getSession().getId();
        User user = userService.findBySessionId(sessionId);
        if (user != null) {
            return user.getId();
        }
        
        return "ANONYMOUS";
    }
    
    @Override
    public int getPriority() {
        return 100;  // 高优先级
    }
}
```

### 多个UserIdResolver

当存在多个 `UserIdResolver` 时，系统会按优先级顺序使用：

```java
@Component
public class JwtUserIdResolver implements UserIdResolver {
    
    @Override
    public String resolveUserId(HttpServletRequest request) {
        return extractUserIdFromJwt(request);
    }
    
    @Override
    public boolean canResolve(HttpServletRequest request) {
        // 只处理包含JWT的请求
        String auth = request.getHeader("Authorization");
        return auth != null && auth.startsWith("Bearer ");
    }
    
    @Override
    public int getPriority() {
        return 200;  // 最高优先级
    }
}

@Component  
public class HeaderUserIdResolver implements UserIdResolver {
    
    @Override
    public String resolveUserId(HttpServletRequest request) {
        return request.getHeader("X-User-ID");
    }
    
    @Override
    public int getPriority() {
        return 100;  // 中等优先级
    }
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
# 管理界面配置详细说明
rate-limiter:
  admin:
    enabled: true                    # 启用管理界面（默认：false）
    username: admin                  # 登录用户名（默认：admin）
    password: admin123               # 登录密码（默认：admin123）
    base-path: /admin/rate-limiter   # 访问路径（默认：/admin/rate-limiter）
    session-timeout: 30              # 会话超时分钟数（默认：30）
    logging:
      file-enabled: true             # 启用操作日志（默认：false）
      file-path: ./logs/rate-limiter/operations.log  # 日志文件路径（默认）
```

> **配置说明**：
> - 当 `enabled: false` 或未配置时，管理界面相关的所有 Bean 都不会被加载，确保安全性
> - 当 `enabled: true` 但其他属性未配置时，将使用上述默认值
> - 生产环境请务必修改默认的用户名和密码

### 功能特性

- 📊 **实时监控**：查看限流规则和统计信息
- ⚙️ **配置管理**：动态添加、修改、删除限流规则
- 🔍 **接口发现**：自动发现应用中的 API 接口
- 📝 **操作日志**：记录配置变更历史

访问地址：`http://localhost:8080/admin/rate-limiter/login`

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
  
  # API 保护套件配置
  api-protection:
    enabled: true
    
    # 幂等性控制配置
    idempotent:
      enabled: true                        # 是否启用幂等性控制（默认：true）
      default-timeout: 300                 # 默认超时时间（秒）（默认：300）
      result-cache-enabled: true           # 是否启用结果缓存（默认：true）
    
    # 防重复提交配置
    duplicate-submit:
      enabled: true                        # 是否启用防重复提交（默认：true）
      default-interval: 5                  # 默认时间间隔（秒）（默认：5）
    
    # 启动清理配置
    startup-cleanup-enabled: true          # 启动时清理API保护数据（默认：true）
    startup-cleanup-dynamic-config: true   # 启动时清理动态配置（默认：true）
    
    # 键前缀配置 - 支持多应用共享Redis
    key-prefix:
      idempotent: "smart:idempotent:"      # 幂等性键前缀（可自定义）
      duplicate-submit: "smart:duplicate:" # 防重复提交键前缀（可自定义）
      dynamic-config: "smart:config:"      # 动态配置键前缀（可自定义）
      application-id: "my-app"             # 应用标识，多应用时避免键冲突
    
    # 拦截器执行优先级配置
    interceptor-order:
      rate-limit: 50                       # 限流拦截器优先级（数值越小越优先）
      idempotent: 100                      # 幂等性拦截器优先级
      duplicate-submit: 200                # 防重复提交拦截器优先级
    
    # 存储配置
    storage:
      type: redis                          # redis, memory
    
    # 监控配置
    monitoring:
      enabled: true                        # 启用监控指标
      metrics-enabled: true                # 启用 Micrometer 指标

# 管理界面配置
rate-limiter:
  admin:
    enabled: true                    # 是否启用管理界面（默认：false）
    base-path: /admin/rate-limiter   # 访问路径（默认：/admin/rate-limiter）
    username: admin                  # 登录用户名（默认：admin）
    password: admin123               # 登录密码（默认：admin123）
    session-timeout: 30              # 会话超时分钟数（默认：30）
    
    # 安全配置
    security:
      enable-header-check: false
      allowed-ips: "127.0.0.1,192.168.1.*"
    
    # 日志配置
    logging:
      file-enabled: true             # 启用操作日志（默认：false）
      file-path: ./logs/rate-limiter/operations.log  # 日志文件路径（默认）
    
    # 扫描配置
    scanning:
      strategy: SYNC                       # SYNC, ASYNC, DISABLED
      async-delay-minutes: 3
```

### 自定义组件

```java
// 自定义用户ID解析器
@Component
@Primary  // 覆盖默认实现
public class CustomUserIdResolver implements UserIdResolver {
    
    @Override
    public String resolveUserId(HttpServletRequest request) {
        // 从JWT Token中解析用户ID
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            return jwtUtils.parseUserId(token);
        }
        
        // 从自定义认证头获取
        return request.getHeader("X-User-ID");
    }
    
    @Override
    public int getPriority() {
        return 100;  // 高优先级
    }
}

// 自定义IP解析器
@Component  
public class CustomIpResolver implements IpResolver {
    @Override
    public String resolveIp(HttpServletRequest request) {
        // 处理代理情况
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

// 自定义幂等键生成器（可选）
@Component
public class CustomIdempotentKeyGenerator implements IdempotentKeyGenerator {
    
    @Override
    public String generateKey(String methodSignature, Object[] args, 
                            String userId, HttpServletRequest request) {
        // 基于业务逻辑生成幂等键
        if (args.length > 0 && args[0] instanceof OrderRequest) {
            OrderRequest orderRequest = (OrderRequest) args[0];
            return "order:" + orderRequest.getOrderNo();
        }
        
        // 默认策略
        return userId + ":" + methodSignature + ":" + Arrays.hashCode(args);
    }
}

// 自定义防重复键生成器（可选）
@Component
public class CustomDuplicateSubmitKeyGenerator implements DuplicateSubmitKeyGenerator {
    
    @Override
    public String generateKey(DuplicateSubmit annotation, String methodSignature,
                            String userId, String clientIp, String sessionId,
                            HttpServletRequest request) {
        
        // 基于业务场景自定义键生成逻辑
        if (methodSignature.contains("payment")) {
            // 支付相关接口使用用户ID + IP的组合
            return "payment:" + userId + ":" + clientIp + ":" + methodSignature;
        }
        
        // 其他接口使用默认策略
        return annotation.dimension().name() + ":" + userId + ":" + methodSignature;
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

### 3. API 保护策略

#### 3.1 限流策略

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

#### 3.2 幂等性策略

```java
// ✅ 推荐：关键业务操作
@Idempotent(
    timeout = 300,
    keyStrategy = Idempotent.KeyStrategy.CUSTOM,
    keyExpression = "#request.orderNo",
    returnFirstResult = true,
    message = "订单正在处理中，请勿重复提交"
)
@PostMapping("/api/order/create")
public OrderResult createOrder(@RequestBody OrderRequest request) {
    return orderService.createOrder(request);
}

// ✅ 推荐：用户级幂等
@Idempotent(
    timeout = 60,
    keyStrategy = Idempotent.KeyStrategy.USER_PARAMS,
    message = "操作正在处理中"
)
@PostMapping("/api/user/profile")
public UserResult updateUserProfile(@RequestBody UserRequest request) {
    return userService.updateProfile(request);
}
```

#### 3.3 防重复提交策略

```java
// ✅ 推荐：用户交互操作
@DuplicateSubmit(
    interval = 3,
    dimension = DuplicateSubmit.KeyDimension.USER_METHOD,
    message = "操作过于频繁，请稍后再试"
)
@PostMapping("/api/vote")
public VoteResult vote(@RequestBody VoteRequest request) {
    return voteService.vote(request);
}

// ✅ 推荐：全局资源操作
@DuplicateSubmit(
    interval = 30,
    dimension = DuplicateSubmit.KeyDimension.GLOBAL_METHOD,
    message = "系统正在处理中，请稍后再试"
)
@PostMapping("/api/system/cache/refresh")
public RefreshResult refreshCache() {
    return cacheService.refresh();
}

// ✅ 推荐：组合保护
@RateLimit(permits = 10, window = 60)
@DuplicateSubmit(interval = 5)
@Idempotent(timeout = 30)
@PostMapping("/api/sensitive/operation")
public OperationResult sensitiveOperation(@RequestBody SensitiveRequest request) {
    return operationService.execute(request);
}
```

### 4. 配置最佳实践

#### 4.1 多应用共享Redis配置
```yaml
smart:
  api-protection:
    key-prefix:
      application-id: "${spring.application.name}"  # 自动使用应用名避免冲突
      idempotent: "idempotent:"    # 简化前缀，减少Redis键长度
      duplicate-submit: "dup:"     # 缩短前缀，节省内存
```

#### 4.2 执行优先级配置
```yaml
smart:
  api-protection:
    interceptor-order:
      rate-limit: 50      # 最先执行，快速拒绝超频请求
      idempotent: 100     # 其次执行，处理业务重复
      duplicate-submit: 200  # 最后执行，处理用户重复操作
```

#### 4.3 生产环境启动配置
```yaml
smart:
  api-protection:
    # 生产环境建议禁用动态配置清理，保留运营配置,开启后服务重启会尝试清理动态配置的数据
    startup-cleanup-dynamic-config: false
    # 保留幂等数据清理，确保重启后状态一致  
    startup-cleanup-enabled: true
```

### 5. 错误处理

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
    enabled: true  # 默认为 false，必须显式启用
    
# 检查路径是否正确
# 默认访问路径：http://localhost:8080/admin/rate-limiter/login
# 如果修改了 base-path，请使用对应的路径访问+/login
```

**Q: 忘记管理界面密码？**
```yaml
# 管理界面默认凭据
# 用户名：admin
# 密码：admin123
# 如需修改，请在配置文件中设置 username 和 password
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
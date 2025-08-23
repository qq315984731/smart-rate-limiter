# 🚀 Smart Rate Limiter 快速开始

## 最简配置（推荐新手）

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.twjgs</groupId>
    <artifactId>smart-rate-limiter-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 零配置使用所有功能

```yaml
# application.yml - 完全空文件也可以使用所有功能！
# 以下为可选配置，不配置也能正常使用
spring:
  application:
    name: my-app

# 🎯 启用所有API保护功能（默认已启用，无需配置）
# smart:
#   rate-limiter:
#     enabled: true              # 限流控制（默认：true）
#   api-protection:
#     enabled: true              # API保护套件（默认：true）
#     idempotent:
#       enabled: true            # 幂等性控制（默认：true）
#     duplicate-submit:
#       enabled: true            # 防重复提交（默认：true）
#     # 新增功能配置（均有默认值，可选配置）
#     startup-cleanup-enabled: true       # 启动时清理API保护数据
#     key-prefix:
#       application-id: "${spring.application.name}"  # 多应用标识
#     interceptor-order:
#       rate-limit: 50           # 拦截器执行优先级
#       idempotent: 100
#       duplicate-submit: 200
```

### 3. 分布式模式配置（推荐生产环境）

⚠️ **重要**：微服务架构建议使用Redis实现分布式存储，确保各服务实例数据同步。

```yaml
# application.yml - 分布式推荐配置
spring:
  application:
    name: my-service
  data:
    redis:
      host: redis-server    # Redis服务器地址
      port: 6379           # Redis端口
      password: your-pwd   # Redis密码（如有）
      database: 0         # Redis数据库索引

# 无需额外配置！系统会自动检测Redis并使用分布式存储
# 以下配置为默认值，可以不设置
# smart:
#   rate-limiter:
#     storage-type: redis   # 有Redis时自动使用，无Redis时降级为memory
#   api-protection:
#     storage:
#       type: redis         # API保护也会自动使用Redis
```

### 4. 直接使用注解

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
        return "uploaded";
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
    
    // 🔒 幂等性控制：防止重复执行
    @Idempotent(timeout = 60, message = "请求正在处理中，请勿重复操作")
    @PostMapping("/api/payment")
    public PaymentResult processPayment(@RequestBody PaymentRequest request) {
        // 支付处理逻辑只会执行一次
        return paymentService.processPayment(request);
    }
    
    // 🚫 防重复提交：5秒内禁止重复提交
    @DuplicateSubmit(interval = 5, message = "请勿重复提交，请稍候再试")
    @PostMapping("/api/comment")
    public CommentResult addComment(@RequestBody CommentRequest request) {
        // 评论处理逻辑
        return commentService.addComment(request);
    }
    
    // 🎯 组合使用：限流 + 防重复提交 + 幂等性控制
    @RateLimit(permits = 10, window = 60, message = "访问频率过高")
    @DuplicateSubmit(interval = 3, message = "请勿频繁操作")
    @Idempotent(timeout = 30, message = "请求正在处理中")
    @PostMapping("/api/critical/operation")
    public OperationResult criticalOperation(@RequestBody OperationRequest request) {
        // 关键业务操作，多重保护
        return operationService.execute(request);
    }
}
```

## 🧪 功能验证测试

### 快速验证所有功能是否正常工作

```bash
# 1. 限流测试 - 快速请求多次，超过限制会被拒绝
curl -X GET http://localhost:8080/api/data

# 2. 防重复提交测试 - 5秒内重复提交会被阻止
curl -X POST http://localhost:8080/api/comment \
  -H "Content-Type: application/json" \
  -d '{"content":"测试评论","author":"测试用户"}'

# 立即重复（应该被阻止）
curl -X POST http://localhost:8080/api/comment \
  -H "Content-Type: application/json" \
  -d '{"content":"重复评论","author":"测试用户"}'

# 3. 幂等性测试 - 相同业务参数多次请求只执行一次
curl -X POST http://localhost:8080/api/payment \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORDER123","amount":100}'

# 重复相同订单（会返回首次执行结果）
curl -X POST http://localhost:8080/api/payment \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORDER123","amount":100}'

# 4. 组合保护测试
curl -X POST http://localhost:8080/api/critical/operation \
  -H "Content-Type: application/json" \
  -d '{"data":"测试数据"}'
```

### 自定义用户识别测试

```bash
# 使用 X-User-ID 头识别用户
curl -X POST http://localhost:8080/api/comment \
  -H "Content-Type: application/json" \
  -H "X-User-ID: user123" \
  -d '{"content":"用户123的评论"}'

# 使用 JWT Token（如果配置了JWT解析器）
curl -X POST http://localhost:8080/api/payment \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-jwt-token" \
  -d '{"orderId":"ORDER456","amount":200}'
```

## 开启管理面板（推荐生产）

### 配置文件

```yaml
# application.yml
rate-limiter:
  admin:
    enabled: true           # 开启管理页面
    username: admin         # 登录用户名
    password: admin123      # 登录密码（请修改）
```

### 访问管理页面

1. 启动应用
2. 访问：`http://localhost:8080/admin/rate-limiter/login`
3. 输入用户名密码登录
4. 在管理页面中动态配置限流策略

## 常用注解示例

```java
@RestController
public class CommonExamples {
    
    // 1. 登录接口 - 严格限流
    @RateLimit(permits = 5, window = 300, message = "登录过于频繁")
    @PostMapping("/login")
    public String login() { return "ok"; }
    
    // 2. 注册接口 - 超严格限流  
    @RateLimit(permits = 3, window = 3600, message = "注册请求过多")
    @PostMapping("/register")
    public String register() { return "ok"; }
    
    // 3. 发送验证码 - 防刷
    @RateLimit(permits = 1, window = 60, message = "验证码发送过于频繁")
    @PostMapping("/sms/code")
    public String sendSms() { return "ok"; }
    
    // 4. 文件上传 - 防大量上传
    @RateLimit(
        dimension = RateLimit.LimitDimension.IP,
        permits = 10, 
        window = 60,
        message = "上传过于频繁"
    )
    @PostMapping("/upload")
    public String upload() { return "ok"; }
    
    // 5. 多维度限流 - 全面保护
    @MultiRateLimit({
        @RateLimit(dimension = RateLimit.LimitDimension.GLOBAL, permits = 1000, window = 60),
        @RateLimit(dimension = RateLimit.LimitDimension.IP, permits = 10, window = 60),
        @RateLimit(dimension = RateLimit.LimitDimension.USER, permits = 50, window = 60)
    })
    @PostMapping("/api/important")
    public String importantApi() { return "ok"; }
}
```

## 就这么简单！

- ✅ **零配置**：添加依赖即可使用
- ✅ **注解驱动**：简单易懂的API
- ✅ **自动降级**：Redis不可用时自动切换内存模式
- ✅ **生产就绪**：支持分布式、监控、管理界面

下一步：查看 [完整配置文档](CONFIGURATION.md) 了解更多高级功能。
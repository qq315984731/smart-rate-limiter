# 🚀 Smart Rate Limiter 快速开始

## 最简配置（推荐新手）

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github</groupId>
    <artifactId>smart-rate-limiter-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 单机模式（默认）

```yaml
# application.yml - 可以完全为空！
spring:
  application:
    name: my-app
```

### 3. 微服务/分布式模式（推荐生产）

⚠️ **重要**：在微服务架构中，使用内存限流会导致各服务实例独立计算，限流效果不准确。**强烈建议使用Redis实现分布式限流**。

```yaml
# application.yml - 微服务推荐配置
spring:
  application:
    name: my-service
  data:
    redis:
      host: redis-server    # Redis服务器地址
      port: 6379           # Redis端口
      password: your-pwd   # Redis密码（如有）
      database: 0         # Redis数据库索引

smart:
  rate-limiter:
    storage-type: redis   # 使用Redis存储（分布式）
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
}
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
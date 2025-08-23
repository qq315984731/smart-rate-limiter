package com.twjgs.ratelimiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 简化的测试应用程序
 * 
 * <p>使用独立的控制器文件来演示 Smart Rate Limiter 功能
 * 
 * @author Smart Rate Limiter Team  
 * @since 1.1.0
 */
@SpringBootApplication
@Import(TestConfiguration.class)
public class SimpleTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleTestApplication.class, args);
    }
}
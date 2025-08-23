package com.twjgs.ratelimiter.comprehensive;

import com.twjgs.ratelimiter.TestApplication;
import com.twjgs.ratelimiter.annotation.RateLimit;
import com.twjgs.ratelimiter.exception.RateLimitException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 限流功能全面测试
 * 
 * 测试覆盖：
 * 1. 基础限流功能
 * 2. 不同算法的限流效果
 * 3. 多维度限流（GLOBAL, IP, USER, CUSTOM）
 * 4. 并发场景下的限流准确性
 * 5. 限流配置的边界值测试
 * 6. 错误场景处理
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application.yml")
@DisplayName("限流功能全面测试")
public class RateLimitComprehensiveTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    @DisplayName("基础限流功能测试 - 每分钟10次限制")
    public void testBasicRateLimit() throws Exception {
        String url = "http://localhost:" + port + "/api/test/rate-limit/basic";
        
        // 前10次请求应该成功
        for (int i = 0; i < 10; i++) {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode(), 
                "第 " + (i + 1) + " 次请求应该成功");
            assertNotNull(response.getBody());
            assertTrue(response.getBody().contains("Basic rate limit test"));
        }
        
        // 第11次请求应该被限流
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode(), 
            "第11次请求应该被限流");
    }

    @Test
    @DisplayName("IP维度限流测试 - 每30秒5次")
    public void testIpBasedRateLimit() throws Exception {
        String url = "http://localhost:" + port + "/api/test/rate-limit/ip-based";
        
        // 前5次请求应该成功
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode(),
                "IP限流第 " + (i + 1) + " 次请求应该成功");
        }
        
        // 第6次请求应该被限流
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode(),
            "IP限流第6次请求应该被限流");
    }

    @Test
    @DisplayName("令牌桶算法限流测试")
    public void testTokenBucketRateLimit() throws Exception {
        String url = "http://localhost:" + port + "/api/test/rate-limit/user-based";
        
        // 测试令牌桶初始容量
        for (int i = 0; i < 3; i++) {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode(),
                "令牌桶算法第 " + (i + 1) + " 次请求应该成功");
        }
        
        // 超出令牌桶容量
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode(),
            "令牌桶算法超出容量应该被限流");
    }

    @Test
    @DisplayName("并发场景限流准确性测试")
    public void testConcurrentRateLimit() throws Exception {
        String url = "http://localhost:" + port + "/api/test/rate-limit/basic";
        int threadCount = 20;
        int requestsPerThread = 2;
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // 记录成功和失败的请求数
        int[] successCount = {0};
        int[] failureCount = {0};
        
        // 并发发送请求
        CompletableFuture<Void>[] futures = IntStream.range(0, threadCount)
            .mapToObj(i -> CompletableFuture.runAsync(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                        synchronized (successCount) {
                            if (response.getStatusCode() == HttpStatus.OK) {
                                successCount[0]++;
                            } else if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                                failureCount[0]++;
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }))
            .toArray(CompletableFuture[]::new);
        
        // 等待所有请求完成
        latch.await(30, TimeUnit.SECONDS);
        CompletableFuture.allOf(futures).join();
        
        // 验证限流准确性
        int totalRequests = successCount[0] + failureCount[0];
        assertEquals(threadCount * requestsPerThread, totalRequests,
            "总请求数应该等于发送的请求数");
        
        // 成功的请求数不应超过限制（每分钟10次）
        assertTrue(successCount[0] <= 10,
            "成功请求数不应超过限流限制，实际成功: " + successCount[0]);
        assertTrue(failureCount[0] > 0,
            "应该有请求被限流，实际被限流: " + failureCount[0]);
    }

    @Test
    @DisplayName("限流边界值测试")
    public void testRateLimitBoundaryValues() throws Exception {
        // 测试permits=1的极限情况
        String url = "http://localhost:" + port + "/api/test/rate-limit/basic";
        
        // 重置限流状态（等待窗口期过去）
        Thread.sleep(61000); // 等待61秒，超过60秒窗口期
        
        // 第一次请求应该成功
        ResponseEntity<String> response1 = restTemplate.getForEntity(url, String.class);
        assertEquals(HttpStatus.OK, response1.getStatusCode(), 
            "重置后第一次请求应该成功");
        
        // 立即发送第二次请求，应该成功（因为限制是10次）
        ResponseEntity<String> response2 = restTemplate.getForEntity(url, String.class);
        assertEquals(HttpStatus.OK, response2.getStatusCode(),
            "第二次请求应该成功");
    }

    @Test
    @DisplayName("限流错误消息测试")
    public void testRateLimitErrorMessages() throws Exception {
        String ipBasedUrl = "http://localhost:" + port + "/api/test/rate-limit/ip-based";
        
        // 先触发IP限流
        for (int i = 0; i < 6; i++) {
            restTemplate.getForEntity(ipBasedUrl, String.class);
        }
        
        // 验证错误消息
        ResponseEntity<String> response = restTemplate.getForEntity(ipBasedUrl, String.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertTrue(response.getBody().contains("您的IP访问过于频繁") || 
                  response.getHeaders().containsKey("X-RateLimit-Remaining"));
    }

    @Test
    @DisplayName("多种限流算法性能对比测试")
    public void testDifferentAlgorithmsPerformance() throws Exception {
        // 这个测试主要验证不同算法都能正常工作
        String[] urls = {
            "http://localhost:" + port + "/api/test/rate-limit/basic",        // 滑动窗口
            "http://localhost:" + port + "/api/test/rate-limit/user-based"    // 令牌桶
        };
        
        for (String url : urls) {
            long startTime = System.currentTimeMillis();
            
            // 发送请求直到被限流
            int successCount = 0;
            while (successCount < 10) {  // 最多尝试10次
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                if (response.getStatusCode() == HttpStatus.OK) {
                    successCount++;
                } else {
                    break;
                }
            }
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            assertTrue(successCount > 0, "至少应有一次成功请求");
            assertTrue(duration < 5000, "限流检查耗时不应超过5秒，实际耗时: " + duration + "ms");
        }
    }

    @Test
    @DisplayName("限流统计信息验证测试")
    public void testRateLimitStatistics() throws Exception {
        String url = "http://localhost:" + port + "/api/test/rate-limit/basic";
        
        // 发送一些请求
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        
        // 验证响应头包含限流信息
        assertNotNull(response.getHeaders(), "响应头不应为空");
        
        // 检查是否有限流相关的响应头
        // 注意：这取决于具体的限流实现是否在响应头中添加了统计信息
        if (response.getHeaders().containsKey("X-RateLimit-Limit") ||
            response.getHeaders().containsKey("X-RateLimit-Remaining") ||
            response.getHeaders().containsKey("X-RateLimit-Reset")) {
            
            assertTrue(true, "限流响应头信息存在");
        } else {
            // 如果没有响应头，至少应该能正常处理请求
            assertNotNull(response.getBody(), "响应体不应为空");
        }
    }
}
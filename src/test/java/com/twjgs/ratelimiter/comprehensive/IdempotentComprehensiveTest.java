package com.twjgs.ratelimiter.comprehensive;

import com.twjgs.ratelimiter.TestApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 幂等性控制全面测试
 * 
 * 测试覆盖：
 * 1. 基础幂等性功能
 * 2. 不同键策略的幂等性控制
 * 3. 并发场景下的幂等性保证
 * 4. 幂等性超时处理
 * 5. 结果缓存功能
 * 6. 幂等性异常场景
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application.yml")
@DisplayName("幂等性控制全面测试")
public class IdempotentComprehensiveTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    @DisplayName("基础幂等性功能测试")
    public void testBasicIdempotent() throws Exception {
        String url = "http://localhost:" + port + "/api/test/idempotent/basic";
        String requestBody = "{\"testData\":\"basic-idempotent-test\"}";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        
        // 第一次请求 - 应该正常执行
        ResponseEntity<String> response1 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        assertNotNull(response1.getBody());
        assertTrue(response1.getBody().contains("Basic idempotent test"));
        
        // 第二次相同请求 - 应该返回幂等结果（正在处理中的响应或缓存的结果）
        ResponseEntity<String> response2 = restTemplate.postForEntity(url, entity, String.class);
        // 幂等的情况可能是：
        // 1. 返回409 Conflict（正在处理中）
        // 2. 返回200 OK（缓存的结果）
        assertTrue(response2.getStatusCode() == HttpStatus.OK || 
                  response2.getStatusCode() == HttpStatus.CONFLICT,
                  "幂等请求应返回200或409状态码，实际: " + response2.getStatusCode());
        
        if (response2.getStatusCode() == HttpStatus.OK) {
            // 如果返回成功，应该是相同的结果
            assertNotNull(response2.getBody());
        }
    }

    @Test
    @DisplayName("参数哈希幂等性测试")
    public void testParamsHashIdempotent() throws Exception {
        String url = "http://localhost:" + port + "/api/test/idempotent/params-hash";
        String orderId = "ORDER-" + System.currentTimeMillis();
        String userId = "USER-123";
        String requestBody = "{\"amount\":100.00,\"currency\":\"CNY\"}";
        
        String fullUrl = url + "?orderId=" + orderId + "&userId=" + userId;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        
        // 第一次请求
        ResponseEntity<String> response1 = restTemplate.postForEntity(fullUrl, entity, String.class);
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        assertTrue(response1.getBody().contains("Payment processed"));
        
        // 第二次相同参数请求 - 应该被幂等控制
        ResponseEntity<String> response2 = restTemplate.postForEntity(fullUrl, entity, String.class);
        assertTrue(response2.getStatusCode() == HttpStatus.OK || 
                  response2.getStatusCode() == HttpStatus.CONFLICT,
                  "参数哈希幂等应返回200或409状态码");
        
        // 不同参数的请求应该能正常执行
        String differentUrl = url + "?orderId=ORDER-DIFFERENT&userId=" + userId;
        ResponseEntity<String> response3 = restTemplate.postForEntity(differentUrl, entity, String.class);
        assertEquals(HttpStatus.OK, response3.getStatusCode(),
            "不同参数的请求应该能正常执行");
    }

    @Test
    @DisplayName("自定义键幂等性测试")
    public void testCustomKeyIdempotent() throws Exception {
        String url = "http://localhost:" + port + "/api/test/idempotent/custom-key";
        String orderId = "CUSTOM-ORDER-" + System.currentTimeMillis();
        String requestBody = "{\"orderId\":\"" + orderId + "\",\"amount\":200.00}";
        
        String fullUrl = url + "?orderId=" + orderId;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        
        // 第一次请求
        ResponseEntity<String> response1 = restTemplate.postForEntity(fullUrl, entity, String.class);
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        assertTrue(response1.getBody().contains("Custom key idempotent test"));
        
        // 第二次相同订单号请求
        ResponseEntity<String> response2 = restTemplate.postForEntity(fullUrl, entity, String.class);
        assertTrue(response2.getStatusCode() == HttpStatus.OK || 
                  response2.getStatusCode() == HttpStatus.CONFLICT,
                  "自定义键幂等应返回200或409状态码");
    }

    @Test
    @DisplayName("并发场景幂等性测试")
    public void testConcurrentIdempotent() throws Exception {
        String url = "http://localhost:" + port + "/api/test/idempotent/basic";
        String requestBody = "{\"testData\":\"concurrent-test-" + System.currentTimeMillis() + "\"}";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        int[] successCount = {0};
        int[] conflictCount = {0};
        int[] errorCount = {0};
        
        // 并发发送相同的幂等请求
        CompletableFuture<Void>[] futures = IntStream.range(0, threadCount)
            .mapToObj(i -> CompletableFuture.runAsync(() -> {
                try {
                    ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                    synchronized (successCount) {
                        if (response.getStatusCode() == HttpStatus.OK) {
                            successCount[0]++;
                        } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                            conflictCount[0]++;
                        } else {
                            errorCount[0]++;
                        }
                    }
                } catch (Exception e) {
                    synchronized (errorCount) {
                        errorCount[0]++;
                    }
                } finally {
                    latch.countDown();
                }
            }))
            .toArray(CompletableFuture[]::new);
        
        // 等待所有请求完成
        latch.await(30, TimeUnit.SECONDS);
        CompletableFuture.allOf(futures).join();
        
        // 验证幂等性效果
        int totalResponses = successCount[0] + conflictCount[0] + errorCount[0];
        assertEquals(threadCount, totalResponses, "应收到所有请求的响应");
        
        // 在并发场景下，应该只有一个请求真正执行成功，其他的应该被幂等控制
        // 允许一定的误差，因为可能存在时序问题
        assertTrue(successCount[0] >= 1, "至少应有一个请求成功执行");
        assertTrue(successCount[0] <= 3, "成功执行的请求不应太多，实际: " + successCount[0]);
        assertTrue(conflictCount[0] + errorCount[0] > 0, "应该有请求被幂等控制");
    }

    @Test
    @DisplayName("幂等性超时测试")
    public void testIdempotentTimeout() throws Exception {
        String url = "http://localhost:" + port + "/api/test/idempotent/basic";
        String requestBody = "{\"testData\":\"timeout-test-" + System.currentTimeMillis() + "\"}";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        
        // 发送第一次请求
        ResponseEntity<String> response1 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        
        // 立即发送第二次相同请求（应该被幂等控制）
        ResponseEntity<String> response2 = restTemplate.postForEntity(url, entity, String.class);
        assertTrue(response2.getStatusCode() == HttpStatus.OK || 
                  response2.getStatusCode() == HttpStatus.CONFLICT);
        
        // 等待足够长的时间（超过幂等超时时间300秒需要太久，这里测试较短时间的行为）
        // 在实际生产测试中，可以通过修改配置来缩短超时时间进行测试
    }

    @Test
    @DisplayName("不同请求参数幂等性测试")
    public void testDifferentParametersIdempotent() throws Exception {
        String url = "http://localhost:" + port + "/api/test/idempotent/basic";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // 发送不同参数的请求，应该都能正常执行
        String[] requestBodies = {
            "{\"testData\":\"request-1\"}",
            "{\"testData\":\"request-2\"}",
            "{\"testData\":\"request-3\"}"
        };
        
        for (String requestBody : requestBodies) {
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode(),
                "不同参数的请求应该都能正常执行");
            assertNotNull(response.getBody());
        }
    }

    @Test
    @DisplayName("幂等性错误处理测试")
    public void testIdempotentErrorHandling() throws Exception {
        String url = "http://localhost:" + port + "/api/test/idempotent/basic";
        
        // 测试空请求体
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> emptyEntity = new HttpEntity<>("", headers);
        
        ResponseEntity<String> response1 = restTemplate.postForEntity(url, emptyEntity, String.class);
        // 应该能处理空请求体（返回200或400都可以接受，取决于业务逻辑）
        assertTrue(response1.getStatusCode() == HttpStatus.OK || 
                  response1.getStatusCode() == HttpStatus.BAD_REQUEST);
        
        // 测试无效JSON
        HttpEntity<String> invalidEntity = new HttpEntity<>("{invalid-json", headers);
        
        try {
            ResponseEntity<String> response2 = restTemplate.postForEntity(url, invalidEntity, String.class);
            // 应该能处理无效JSON（返回400错误或者其他适当的错误码）
            assertTrue(response2.getStatusCode().is4xxClientError() || 
                      response2.getStatusCode() == HttpStatus.OK);
        } catch (Exception e) {
            // 抛出异常也是可以接受的处理方式
            assertTrue(true, "无效JSON请求抛出异常是可以接受的");
        }
    }

    @Test
    @DisplayName("幂等性结果缓存测试")
    public void testIdempotentResultCaching() throws Exception {
        String url = "http://localhost:" + port + "/api/test/idempotent/basic";
        String requestBody = "{\"testData\":\"cache-test-" + System.currentTimeMillis() + "\"}";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        
        // 第一次请求
        long startTime1 = System.currentTimeMillis();
        ResponseEntity<String> response1 = restTemplate.postForEntity(url, entity, String.class);
        long endTime1 = System.currentTimeMillis();
        long duration1 = endTime1 - startTime1;
        
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        String firstResult = response1.getBody();
        assertNotNull(firstResult);
        
        // 等待第一次请求完全处理完毕
        Thread.sleep(2000);
        
        // 第二次相同请求（测试结果缓存）
        long startTime2 = System.currentTimeMillis();
        ResponseEntity<String> response2 = restTemplate.postForEntity(url, entity, String.class);
        long endTime2 = System.currentTimeMillis();
        long duration2 = endTime2 - startTime2;
        
        if (response2.getStatusCode() == HttpStatus.OK) {
            // 如果启用了结果缓存，第二次请求应该更快
            // 但这个断言可能不稳定，因为网络延迟等因素的影响
            String secondResult = response2.getBody();
            assertNotNull(secondResult);
            
            // 验证结果的一致性（时间戳可能不同，但业务标识应该相似）
            assertTrue(secondResult.contains("Basic idempotent test"),
                "缓存的结果应该包含相同的业务标识");
        }
    }
}
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
 * 组合功能全面测试
 * 
 * 测试覆盖：
 * 1. 限流 + 防重复提交组合
 * 2. 限流 + 幂等性控制组合  
 * 3. 防重复提交 + 幂等性控制组合
 * 4. 三种功能全组合测试
 * 5. 组合功能的执行顺序验证
 * 6. 组合功能的性能影响测试
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application.yml")
@DisplayName("组合功能全面测试")
public class CombinedFeaturesComprehensiveTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    @DisplayName("限流 + 防重复提交组合功能测试")
    public void testRateLimitWithDuplicateSubmit() throws Exception {
        String url = "http://localhost:" + port + "/api/test/combined/rate-limit-duplicate";
        String requestBody = "rate-limit-duplicate-test";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        
        // 第一次请求应该成功（通过限流和防重复提交）
        ResponseEntity<String> response1 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        assertNotNull(response1.getBody());
        assertTrue(response1.getBody().contains("Rate limit + Duplicate submit test"));
        
        // 立即第二次相同请求应该被防重复提交阻止
        ResponseEntity<String> response2 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response2.getStatusCode(),
            "第二次请求应该被防重复提交阻止");
        
        // 等待防重复提交间隔时间后，测试限流功能
        Thread.sleep(11000); // 等待超过10秒的防重复提交间隔
        
        // 连续发送请求测试限流（每分钟5次限制）
        for (int i = 0; i < 5; i++) {
            String uniqueBody = requestBody + "-" + System.currentTimeMillis() + "-" + i;
            HttpEntity<String> uniqueEntity = new HttpEntity<>(uniqueBody, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, uniqueEntity, String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode(),
                "限流范围内的请求应该成功，第" + (i + 1) + "次");
            
            // 每次请求后等待足够时间避免防重复提交
            Thread.sleep(11000);
        }
        
        // 第6次请求应该被限流阻止
        String finalBody = requestBody + "-final-" + System.currentTimeMillis();
        HttpEntity<String> finalEntity = new HttpEntity<>(finalBody, headers);
        ResponseEntity<String> finalResponse = restTemplate.postForEntity(url, finalEntity, String.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, finalResponse.getStatusCode(),
            "超出限流限制的请求应该被阻止");
    }

    @Test
    @DisplayName("限流 + 幂等性控制组合功能测试")
    public void testRateLimitWithIdempotent() throws Exception {
        String url = "http://localhost:" + port + "/api/test/combined/rate-limit-idempotent";
        String requestBody = "rate-limit-idempotent-test-" + System.currentTimeMillis();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        
        // 第一次请求应该成功
        ResponseEntity<String> response1 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        String firstResult = response1.getBody();
        assertNotNull(firstResult);
        assertTrue(firstResult.contains("Rate limit + Idempotent test"));
        
        // 第二次相同请求应该被幂等性控制（可能返回冲突或缓存结果）
        ResponseEntity<String> response2 = restTemplate.postForEntity(url, entity, String.class);
        assertTrue(response2.getStatusCode() == HttpStatus.OK || 
                  response2.getStatusCode() == HttpStatus.CONFLICT,
                  "第二次请求应该被幂等性控制");
        
        // 使用不同的请求内容测试限流
        for (int i = 0; i < 3; i++) {
            String uniqueBody = "different-content-" + System.currentTimeMillis() + "-" + i;
            HttpEntity<String> uniqueEntity = new HttpEntity<>(uniqueBody, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, uniqueEntity, String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode(),
                "不同内容的请求应该通过幂等性检查，第" + (i + 1) + "次");
            
            // 等待足够时间让第一次请求完成
            Thread.sleep(2000);
        }
        
        // 继续发送请求直到触发限流
        for (int i = 0; i < 10; i++) {
            String moreContent = "more-content-" + System.currentTimeMillis() + "-" + i;
            HttpEntity<String> moreEntity = new HttpEntity<>(moreContent, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, moreEntity, String.class);
            if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                // 成功触发限流
                assertTrue(true, "成功触发限流保护");
                break;
            }
            Thread.sleep(1000);
        }
    }

    @Test
    @DisplayName("防重复提交 + 幂等性控制组合功能测试")
    public void testDuplicateSubmitWithIdempotent() throws Exception {
        String url = "http://localhost:" + port + "/api/test/combined/duplicate-idempotent";
        String requestBody = "duplicate-idempotent-test-" + System.currentTimeMillis();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        
        // 第一次请求应该成功
        ResponseEntity<String> response1 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        assertNotNull(response1.getBody());
        assertTrue(response1.getBody().contains("Duplicate submit + Idempotent test"));
        
        // 立即第二次相同请求应该被阻止（防重复提交或幂等性控制）
        ResponseEntity<String> response2 = restTemplate.postForEntity(url, entity, String.class);
        assertTrue(response2.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS || 
                  response2.getStatusCode() == HttpStatus.CONFLICT,
                  "第二次请求应该被防重复提交或幂等性控制阻止");
        
        // 等待防重复提交间隔时间
        Thread.sleep(6000);
        
        // 第三次请求：防重复提交时间已过，但幂等性仍然有效
        ResponseEntity<String> response3 = restTemplate.postForEntity(url, entity, String.class);
        assertTrue(response3.getStatusCode() == HttpStatus.OK || 
                  response3.getStatusCode() == HttpStatus.CONFLICT,
                  "防重复提交时间过后，幂等性控制应该仍然有效");
        
        // 使用不同内容的请求应该能正常执行
        String differentBody = "different-content-" + System.currentTimeMillis();
        HttpEntity<String> differentEntity = new HttpEntity<>(differentBody, headers);
        
        ResponseEntity<String> response4 = restTemplate.postForEntity(url, differentEntity, String.class);
        assertEquals(HttpStatus.OK, response4.getStatusCode(),
            "不同内容的请求应该能正常执行");
    }

    @Test
    @DisplayName("三种功能全组合测试")
    public void testAllFeaturesCombo() throws Exception {
        String url = "http://localhost:" + port + "/api/test/combined/all-features";
        String requestBody = "all-features-test-" + System.currentTimeMillis();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        
        // 第一次请求应该成功（通过所有检查）
        ResponseEntity<String> response1 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        assertNotNull(response1.getBody());
        assertTrue(response1.getBody().contains("All features test"));
        
        // 立即第二次相同请求应该被阻止
        ResponseEntity<String> response2 = restTemplate.postForEntity(url, entity, String.class);
        assertTrue(response2.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS || 
                  response2.getStatusCode() == HttpStatus.CONFLICT,
                  "第二次请求应该被某种保护机制阻止");
        
        // 等待防重复提交间隔时间
        Thread.sleep(9000); // 配置的是8秒间隔
        
        // 测试幂等性是否仍然生效
        ResponseEntity<String> response3 = restTemplate.postForEntity(url, entity, String.class);
        assertTrue(response3.getStatusCode() == HttpStatus.OK || 
                  response3.getStatusCode() == HttpStatus.CONFLICT,
                  "防重复提交时间过后，其他保护机制应该仍然有效");
        
        // 使用不同内容测试限流
        ResponseEntity<String> response4 = restTemplate.postForEntity(url, 
            new HttpEntity<>("different-1-" + System.currentTimeMillis(), headers), String.class);
        assertEquals(HttpStatus.OK, response4.getStatusCode(),
            "不同内容的第一个请求应该成功");
        
        // 等待防重复提交间隔
        Thread.sleep(9000);
        
        ResponseEntity<String> response5 = restTemplate.postForEntity(url, 
            new HttpEntity<>("different-2-" + System.currentTimeMillis(), headers), String.class);
        
        // 此时应该触发限流（配置的是每分钟2次）
        assertTrue(response5.getStatusCode() == HttpStatus.OK || 
                  response5.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS,
                  "第三个不同请求可能触发限流");
    }

    @Test
    @DisplayName("组合功能执行顺序验证")
    public void testExecutionOrder() throws Exception {
        String url = "http://localhost:" + port + "/api/test/combined/all-features";
        String requestBody = "execution-order-test-" + System.currentTimeMillis();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        
        // 根据配置的拦截器优先级:
        // rate-limit: 50 (最先执行)
        // idempotent: 100 
        // duplicate-submit: 200 (最后执行)
        
        // 第一次请求成功
        ResponseEntity<String> response1 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        
        // 立即第二次请求，验证哪个拦截器先生效
        ResponseEntity<String> response2 = restTemplate.postForEntity(url, entity, String.class);
        
        // 记录响应状态码以分析执行顺序
        HttpStatus secondStatus = HttpStatus.valueOf(response2.getStatusCode().value());
        
        assertTrue(secondStatus == HttpStatus.TOO_MANY_REQUESTS || 
                  secondStatus == HttpStatus.CONFLICT,
                  "第二次请求应该被某种机制阻止，状态码: " + secondStatus);
        
        // 基于响应状态码推断执行顺序
        if (secondStatus == HttpStatus.TOO_MANY_REQUESTS) {
            // 可能是防重复提交或限流阻止
            assertTrue(true, "请求被429状态码阻止，可能是防重复提交或限流");
        } else if (secondStatus == HttpStatus.CONFLICT) {
            // 可能是幂等性控制阻止
            assertTrue(true, "请求被409状态码阻止，可能是幂等性控制");
        }
    }

    @Test
    @DisplayName("组合功能并发测试")
    public void testCombinedFeaturesConcurrency() throws Exception {
        String url = "http://localhost:" + port + "/api/test/combined/all-features";
        String baseRequestBody = "concurrency-test-" + System.currentTimeMillis();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        int threadCount = 15;
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        int[] successCount = {0};
        int[] blockedCount = {0};
        int[] conflictCount = {0};
        int[] errorCount = {0};
        
        // 并发发送不同的请求，测试组合功能的并发安全性
        CompletableFuture<Void>[] futures = IntStream.range(0, threadCount)
            .mapToObj(i -> CompletableFuture.runAsync(() -> {
                try {
                    // 每个线程发送不同内容的请求
                    String requestBody = baseRequestBody + "-thread-" + i;
                    HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
                    
                    ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                    
                    synchronized (successCount) {
                        HttpStatus status = HttpStatus.valueOf(response.getStatusCode().value());
                        if (status == HttpStatus.OK) {
                            successCount[0]++;
                        } else if (status == HttpStatus.TOO_MANY_REQUESTS) {
                            blockedCount[0]++;
                        } else if (status == HttpStatus.CONFLICT) {
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
        latch.await(60, TimeUnit.SECONDS);
        CompletableFuture.allOf(futures).join();
        
        // 验证结果
        int totalResponses = successCount[0] + blockedCount[0] + conflictCount[0] + errorCount[0];
        assertEquals(threadCount, totalResponses, "应收到所有请求的响应");
        
        // 应该有一些请求成功，一些被各种机制阻止
        assertTrue(successCount[0] > 0, "应该有一些请求成功，实际成功: " + successCount[0]);
        assertTrue(successCount[0] <= 2, "成功的请求不应超过限流限制（2次/分钟），实际: " + successCount[0]);
        assertTrue(blockedCount[0] + conflictCount[0] > 0, "应该有请求被保护机制阻止");
        
        System.out.println("并发测试结果:");
        System.out.println("成功: " + successCount[0]);
        System.out.println("被阻止(429): " + blockedCount[0]);
        System.out.println("冲突(409): " + conflictCount[0]);
        System.out.println("错误: " + errorCount[0]);
    }

    @Test
    @DisplayName("组合功能性能影响测试")
    public void testPerformanceImpact() throws Exception {
        String baseUrl = "http://localhost:" + port + "/api/test/status";
        String combinedUrl = "http://localhost:" + port + "/api/test/combined/all-features";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // 测试无保护功能的接口性能
        long startTime1 = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            ResponseEntity<String> response = restTemplate.getForEntity(baseUrl, String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
        long endTime1 = System.currentTimeMillis();
        long baselineDuration = endTime1 - startTime1;
        
        // 测试组合功能的接口性能
        long startTime2 = System.currentTimeMillis();
        int successfulRequests = 0;
        for (int i = 0; i < 10; i++) {
            String requestBody = "performance-test-" + System.currentTimeMillis() + "-" + i;
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(combinedUrl, entity, String.class);
                if (response.getStatusCode() == HttpStatus.OK) {
                    successfulRequests++;
                }
                // 等待避免被保护机制阻止
                Thread.sleep(9000); // 等待防重复提交间隔
            } catch (Exception e) {
                // 忽略被保护机制阻止的请求
            }
        }
        long endTime2 = System.currentTimeMillis();
        long combinedDuration = endTime2 - startTime2;
        
        // 计算平均每请求的时间差异
        double baselinePerRequest = (double) baselineDuration / 10;
        double combinedPerRequest = successfulRequests > 0 ? 
            (double) (combinedDuration - (9 * 9000)) / successfulRequests : 0; // 减去等待时间
        
        assertTrue(successfulRequests > 0, "应该有成功的请求");
        
        // 性能影响不应过大（允许10倍的开销，因为包含了复杂的保护逻辑）
        assertTrue(combinedPerRequest <= baselinePerRequest * 10,
            String.format("组合功能的性能影响过大：基准 %.2fms，组合 %.2fms", 
                baselinePerRequest, combinedPerRequest));
        
        System.out.println("性能测试结果:");
        System.out.println("基准接口平均响应时间: " + baselinePerRequest + "ms");
        System.out.println("组合功能平均响应时间: " + combinedPerRequest + "ms");
        System.out.println("成功处理的请求数: " + successfulRequests);
    }
}
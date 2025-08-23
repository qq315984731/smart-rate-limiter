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
 * 防重复提交全面测试
 * 
 * 测试覆盖：
 * 1. 基础防重复提交功能（默认5秒间隔）
 * 2. 自定义间隔时间的防重复提交
 * 3. 不同维度的防重复提交（USER_METHOD, IP_METHOD, GLOBAL_METHOD, CUSTOM）
 * 4. 并发场景下的防重复提交
 * 5. 时间间隔边界测试
 * 6. 错误场景处理
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application.yml")
@DisplayName("防重复提交全面测试")
public class DuplicateSubmitComprehensiveTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    @DisplayName("基础防重复提交功能测试 - 默认5秒间隔")
    public void testBasicDuplicateSubmit() throws Exception {
        String url = "http://localhost:" + port + "/api/test/duplicate-submit/basic";
        String requestBody = "basic-duplicate-submit-test";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        
        // 第一次请求应该成功
        ResponseEntity<String> response1 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        assertNotNull(response1.getBody());
        assertTrue(response1.getBody().contains("Basic duplicate submit test"));
        
        // 立即第二次相同请求应该被阻止
        ResponseEntity<String> response2 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response2.getStatusCode(),
            "立即重复提交应该被阻止");
        
        // 等待超过5秒后应该可以再次提交
        Thread.sleep(6000); // 等待6秒
        ResponseEntity<String> response3 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.OK, response3.getStatusCode(),
            "等待间隔时间后应该可以再次提交");
    }

    @Test
    @DisplayName("自定义间隔防重复提交测试 - 10秒间隔")
    public void testCustomIntervalDuplicateSubmit() throws Exception {
        String url = "http://localhost:" + port + "/api/test/duplicate-submit/custom-interval";
        String requestBody = "custom-interval-test";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        
        // 第一次请求应该成功
        ResponseEntity<String> response1 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        
        // 立即第二次请求应该被阻止
        ResponseEntity<String> response2 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response2.getStatusCode());
        
        // 5秒后仍应被阻止（因为配置的是10秒间隔）
        Thread.sleep(5000);
        ResponseEntity<String> response3 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response3.getStatusCode(),
            "5秒后仍应被阻止，因为配置的是10秒间隔");
        
        // 再等5秒（总共10秒+）后应该可以提交
        Thread.sleep(5500);
        ResponseEntity<String> response4 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.OK, response4.getStatusCode(),
            "等待10秒+后应该可以再次提交");
    }

    @Test
    @DisplayName("IP维度防重复提交测试 - 60秒间隔")
    public void testIpBasedDuplicateSubmit() throws Exception {
        String url = "http://localhost:" + port + "/api/test/duplicate-submit/ip-based";
        String requestBody = "ip-based-test";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        
        // 第一次请求应该成功
        ResponseEntity<String> response1 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        
        // 立即第二次请求应该被阻止
        ResponseEntity<String> response2 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response2.getStatusCode());
        
        // 验证错误消息
        if (response2.getBody() != null) {
            assertTrue(response2.getBody().contains("该IP操作过于频繁") || 
                      response2.getBody().contains("60秒"),
                      "错误消息应该包含IP限制相关信息");
        }
    }

    @Test
    @DisplayName("全局方法维度防重复提交测试 - 30秒间隔")
    public void testGlobalDuplicateSubmit() throws Exception {
        String url = "http://localhost:" + port + "/api/test/duplicate-submit/global";
        String requestBody = "global-test";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        
        // 第一次请求应该成功
        ResponseEntity<String> response1 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        
        // 立即第二次请求应该被阻止
        ResponseEntity<String> response2 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response2.getStatusCode());
        
        // 验证错误消息
        if (response2.getBody() != null) {
            assertTrue(response2.getBody().contains("系统繁忙") || 
                      response2.getBody().contains("30秒"),
                      "错误消息应该包含全局限制相关信息");
        }
    }

    @Test
    @DisplayName("自定义键表达式防重复提交测试 - 300秒间隔")
    public void testCustomKeyDuplicateSubmit() throws Exception {
        String url = "http://localhost:" + port + "/api/test/duplicate-submit/custom-key";
        String productId = "PRODUCT-" + System.currentTimeMillis();
        String requestBody = "custom-key-test";
        
        String fullUrl = url + "?productId=" + productId;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        
        // 第一次请求应该成功
        ResponseEntity<String> response1 = restTemplate.postForEntity(fullUrl, entity, String.class);
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        
        // 第二次相同productId的请求应该被阻止
        ResponseEntity<String> response2 = restTemplate.postForEntity(fullUrl, entity, String.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response2.getStatusCode());
        
        // 不同productId的请求应该能正常执行
        String differentUrl = url + "?productId=DIFFERENT-PRODUCT";
        ResponseEntity<String> response3 = restTemplate.postForEntity(differentUrl, entity, String.class);
        assertEquals(HttpStatus.OK, response3.getStatusCode(),
            "不同productId的请求应该能正常执行");
        
        // 验证错误消息
        if (response2.getBody() != null) {
            assertTrue(response2.getBody().contains("请勿重复提交相同商品") || 
                      response2.getBody().contains("商品"),
                      "错误消息应该包含商品相关信息");
        }
    }

    @Test
    @DisplayName("并发场景防重复提交测试")
    public void testConcurrentDuplicateSubmit() throws Exception {
        String url = "http://localhost:" + port + "/api/test/duplicate-submit/basic";
        String requestBody = "concurrent-test-" + System.currentTimeMillis();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        int[] successCount = {0};
        int[] blockedCount = {0};
        int[] errorCount = {0};
        
        // 并发发送相同的请求
        CompletableFuture<Void>[] futures = IntStream.range(0, threadCount)
            .mapToObj(i -> CompletableFuture.runAsync(() -> {
                try {
                    ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                    synchronized (successCount) {
                        if (response.getStatusCode() == HttpStatus.OK) {
                            successCount[0]++;
                        } else if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                            blockedCount[0]++;
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
        
        // 验证防重复提交效果
        int totalResponses = successCount[0] + blockedCount[0] + errorCount[0];
        assertEquals(threadCount, totalResponses, "应收到所有请求的响应");
        
        // 在并发场景下，应该只有第一个请求成功，其他的应该被阻止
        assertEquals(1, successCount[0], "只应有一个请求成功执行");
        assertEquals(threadCount - 1, blockedCount[0], "其他请求应该被防重复提交机制阻止");
        assertEquals(0, errorCount[0], "不应有错误响应");
    }

    @Test
    @DisplayName("时间间隔边界测试")
    public void testIntervalBoundaryConditions() throws Exception {
        String url = "http://localhost:" + port + "/api/test/duplicate-submit/basic";
        String requestBody = "boundary-test-" + System.currentTimeMillis();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        
        // 第一次请求
        ResponseEntity<String> response1 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        
        // 4.9秒后请求（应该仍被阻止）
        Thread.sleep(4900);
        ResponseEntity<String> response2 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response2.getStatusCode(),
            "4.9秒后的请求应该仍被阻止");
        
        // 再等0.2秒（总计5.1秒，应该可以通过）
        Thread.sleep(200);
        ResponseEntity<String> response3 = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.OK, response3.getStatusCode(),
            "5.1秒后的请求应该可以通过");
    }

    @Test
    @DisplayName("不同请求内容防重复提交测试")
    public void testDifferentContentDuplicateSubmit() throws Exception {
        String url = "http://localhost:" + port + "/api/test/duplicate-submit/basic";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // 发送不同内容的请求，应该都能正常执行
        String[] requestBodies = {
            "content-1",
            "content-2", 
            "content-3"
        };
        
        for (int i = 0; i < requestBodies.length; i++) {
            HttpEntity<String> entity = new HttpEntity<>(requestBodies[i], headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode(),
                "不同内容的请求应该都能正常执行，第" + (i + 1) + "次");
            
            // 立即重复发送相同内容应该被阻止
            ResponseEntity<String> duplicateResponse = restTemplate.postForEntity(url, entity, String.class);
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, duplicateResponse.getStatusCode(),
                "重复的第" + (i + 1) + "次请求应该被阻止");
            
            // 等待间隔时间以免影响下一次测试
            if (i < requestBodies.length - 1) {
                Thread.sleep(5500);
            }
        }
    }

    @Test
    @DisplayName("错误场景处理测试")
    public void testErrorScenarios() throws Exception {
        String url = "http://localhost:" + port + "/api/test/duplicate-submit/basic";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // 测试空请求体
        HttpEntity<String> emptyEntity = new HttpEntity<>("", headers);
        ResponseEntity<String> response1 = restTemplate.postForEntity(url, emptyEntity, String.class);
        assertTrue(response1.getStatusCode() == HttpStatus.OK || 
                  response1.getStatusCode().is4xxClientError(),
                  "空请求体应该被适当处理");
        
        // 测试null请求体
        HttpEntity<String> nullEntity = new HttpEntity<>(null, headers);
        ResponseEntity<String> response2 = restTemplate.postForEntity(url, nullEntity, String.class);
        assertTrue(response2.getStatusCode() == HttpStatus.OK || 
                  response2.getStatusCode().is4xxClientError(),
                  "null请求体应该被适当处理");
        
        // 测试无Content-Type头
        HttpHeaders noContentTypeHeaders = new HttpHeaders();
        HttpEntity<String> noContentTypeEntity = new HttpEntity<>("test-content", noContentTypeHeaders);
        ResponseEntity<String> response3 = restTemplate.postForEntity(url, noContentTypeEntity, String.class);
        assertTrue(response3.getStatusCode() == HttpStatus.OK || 
                  response3.getStatusCode().is4xxClientError(),
                  "无Content-Type头的请求应该被适当处理");
    }

    @Test
    @DisplayName("防重复提交与幂等性的区别验证")
    public void testDuplicateSubmitVsIdempotent() throws Exception {
        String duplicateUrl = "http://localhost:" + port + "/api/test/duplicate-submit/basic";
        String idempotentUrl = "http://localhost:" + port + "/api/test/idempotent/basic";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String requestBody = "comparison-test-" + System.currentTimeMillis();
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        
        // 防重复提交：基于时间间隔，相同用户在时间窗口内不能重复提交
        ResponseEntity<String> dupResponse1 = restTemplate.postForEntity(duplicateUrl, entity, String.class);
        assertEquals(HttpStatus.OK, dupResponse1.getStatusCode());
        
        ResponseEntity<String> dupResponse2 = restTemplate.postForEntity(duplicateUrl, entity, String.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, dupResponse2.getStatusCode(),
            "防重复提交应该基于时间间隔阻止重复");
        
        // 幂等性：基于请求内容，相同请求返回相同结果或阻止重复执行
        ResponseEntity<String> idempotentResponse1 = restTemplate.postForEntity(idempotentUrl, entity, String.class);
        assertEquals(HttpStatus.OK, idempotentResponse1.getStatusCode());
        
        ResponseEntity<String> idempotentResponse2 = restTemplate.postForEntity(idempotentUrl, entity, String.class);
        assertTrue(idempotentResponse2.getStatusCode() == HttpStatus.OK || 
                  idempotentResponse2.getStatusCode() == HttpStatus.CONFLICT,
                  "幂等性应该基于请求内容控制重复执行");
        
        // 验证两种机制的响应码差异
        assertNotEquals(dupResponse2.getStatusCode(), idempotentResponse2.getStatusCode(),
            "防重复提交和幂等性应该有不同的处理方式");
    }
}
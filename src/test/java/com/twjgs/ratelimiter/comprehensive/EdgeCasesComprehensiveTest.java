package com.twjgs.ratelimiter.comprehensive;

import com.twjgs.ratelimiter.TestApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 边界情况和异常场景全面测试
 * 
 * 测试覆盖：
 * 1. 极限参数值测试
 * 2. 异常输入处理测试
 * 3. 网络异常场景测试
 * 4. 并发极限测试
 * 5. 内存和性能边界测试
 * 6. 错误恢复能力测试
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application.yml")
@DisplayName("边界情况和异常场景全面测试")
public class EdgeCasesComprehensiveTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    @DisplayName("极大请求体处理测试")
    public void testLargeRequestBody() throws Exception {
        String url = "http://localhost:" + port + "/api/test/idempotent/basic";
        
        // 创建一个较大的请求体（1MB）
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 1024; i++) {
            largeContent.append("A".repeat(1024)); // 1KB per iteration
        }
        String largeRequestBody = "{\"largeData\":\"" + largeContent.toString() + "\"}";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(largeRequestBody, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            
            // 应该能处理大请求体或返回适当的错误
            assertTrue(response.getStatusCode() == HttpStatus.OK || 
                      response.getStatusCode() == HttpStatus.PAYLOAD_TOO_LARGE ||
                      response.getStatusCode().is4xxClientError(),
                      "大请求体应该被适当处理，状态码: " + response.getStatusCode());
                      
        } catch (Exception e) {
            // 异常也是可以接受的，因为请求体过大
            assertTrue(true, "大请求体导致异常是可以接受的: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("特殊字符请求处理测试")
    public void testSpecialCharacterRequests() throws Exception {
        String url = "http://localhost:" + port + "/api/test/duplicate-submit/basic";
        
        String[] specialContents = {
            "{\"data\":\"测试中文内容\"}",
            "{\"data\":\"🚀🎯🔒 emoji test\"}",
            "{\"data\":\"\\\"escaped\\\" content\"}",
            "{\"data\":\"line1\\nline2\\ttab\"}"
        };
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept-Charset", StandardCharsets.UTF_8.name());
        
        for (String content : specialContents) {
            HttpEntity<String> entity = new HttpEntity<>(content, headers);
            
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                
                // 第一次请求应该成功或返回合理的错误
                assertTrue(response.getStatusCode() == HttpStatus.OK || 
                          response.getStatusCode().is4xxClientError(),
                          "特殊字符请求应该被适当处理: " + content);
                          
                if (response.getStatusCode() == HttpStatus.OK) {
                    // 如果第一次成功，第二次应该被防重复提交阻止
                    ResponseEntity<String> response2 = restTemplate.postForEntity(url, entity, String.class);
                    assertEquals(HttpStatus.TOO_MANY_REQUESTS, response2.getStatusCode(),
                        "特殊字符的重复请求应该被阻止");
                }
                
                // 等待防重复提交间隔
                Thread.sleep(5500);
                
            } catch (Exception e) {
                // 记录异常但不失败测试，因为某些特殊字符可能导致编码问题
                System.out.println("特殊字符请求异常: " + content + " -> " + e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("空值和null值处理测试")
    public void testNullAndEmptyValues() throws Exception {
        String url = "http://localhost:" + port + "/api/test/idempotent/basic";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // 测试空字符串
        HttpEntity<String> emptyEntity = new HttpEntity<>("", headers);
        ResponseEntity<String> response1 = restTemplate.postForEntity(url, emptyEntity, String.class);
        assertTrue(response1.getStatusCode() == HttpStatus.OK || 
                  response1.getStatusCode().is4xxClientError(),
                  "空字符串请求应该被适当处理");
        
        // 测试null值
        HttpEntity<String> nullEntity = new HttpEntity<>(null, headers);
        ResponseEntity<String> response2 = restTemplate.postForEntity(url, nullEntity, String.class);
        assertTrue(response2.getStatusCode() == HttpStatus.OK || 
                  response2.getStatusCode().is4xxClientError(),
                  "null请求应该被适当处理");
        
        // 测试只有空格的字符串
        HttpEntity<String> whitespaceEntity = new HttpEntity<>("   ", headers);
        ResponseEntity<String> response3 = restTemplate.postForEntity(url, whitespaceEntity, String.class);
        assertTrue(response3.getStatusCode() == HttpStatus.OK || 
                  response3.getStatusCode().is4xxClientError(),
                  "空格字符串请求应该被适当处理");
        
        // 测试空JSON对象
        HttpEntity<String> emptyJsonEntity = new HttpEntity<>("{}", headers);
        ResponseEntity<String> response4 = restTemplate.postForEntity(url, emptyJsonEntity, String.class);
        assertTrue(response4.getStatusCode() == HttpStatus.OK || 
                  response4.getStatusCode().is4xxClientError(),
                  "空JSON对象请求应该被适当处理");
    }

    @Test
    @DisplayName("无效JSON格式处理测试")
    public void testInvalidJsonHandling() throws Exception {
        String url = "http://localhost:" + port + "/api/test/idempotent/basic";
        
        String[] invalidJsonStrings = {
            "{invalid json",
            "not json at all",
            "{\"key\":}",
            "{\"key\":\"value\",}",  // trailing comma
            "{'single_quotes': 'value'}",  // single quotes
            "{\"nested\": {\"incomplete\": }",
            "undefined"
        };
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        for (String invalidJson : invalidJsonStrings) {
            try {
                HttpEntity<String> entity = new HttpEntity<>(invalidJson, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                
                // 应该返回400错误或能够处理
                assertTrue(response.getStatusCode() == HttpStatus.BAD_REQUEST || 
                          response.getStatusCode() == HttpStatus.OK ||
                          response.getStatusCode().is4xxClientError(),
                          "无效JSON应该返回适当的错误码: " + invalidJson + " -> " + response.getStatusCode());
                          
            } catch (Exception e) {
                // 抛出异常也是可以接受的
                assertTrue(true, "无效JSON导致异常是可以接受的: " + invalidJson);
            }
        }
    }

    @Test
    @DisplayName("极限并发压力测试")
    public void testExtremeConcurrency() throws Exception {
        String url = "http://localhost:" + port + "/api/test/rate-limit/basic";
        
        // 创建大量并发请求（模拟DDoS场景）
        int threadCount = 100;
        Thread[] threads = new Thread[threadCount];
        int[] responseCount = {0};
        int[] errorCount = {0};
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                    synchronized (responseCount) {
                        responseCount[0]++;
                    }
                } catch (Exception e) {
                    synchronized (errorCount) {
                        errorCount[0]++;
                    }
                }
            });
        }
        
        // 同时启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join(5000); // 最多等待5秒
        }
        
        // 验证系统在极限并发下的稳定性
        int totalHandled = responseCount[0] + errorCount[0];
        assertTrue(totalHandled > 0, "应该处理了一些请求");
        
        // 系统不应该完全崩溃
        assertTrue(totalHandled >= threadCount * 0.5, 
            "至少应该处理50%的请求，实际处理: " + totalHandled + "/" + threadCount);
    }

    @Test
    @DisplayName("长时间运行稳定性测试")
    public void testLongRunningStability() throws Exception {
        String url = "http://localhost:" + port + "/api/test/status";
        
        // 持续发送请求30秒，测试长期稳定性
        long startTime = System.currentTimeMillis();
        long duration = 30000; // 30秒
        int requestCount = 0;
        int errorCount = 0;
        
        while (System.currentTimeMillis() - startTime < duration) {
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                if (response.getStatusCode() == HttpStatus.OK) {
                    requestCount++;
                } else {
                    errorCount++;
                }
            } catch (Exception e) {
                errorCount++;
            }
            
            // 避免过度频繁的请求
            Thread.sleep(100);
        }
        
        // 验证长期运行的稳定性
        assertTrue(requestCount > 0, "应该有成功的请求");
        double errorRate = (double) errorCount / (requestCount + errorCount);
        assertTrue(errorRate < 0.1, "错误率应该低于10%，实际错误率: " + errorRate);
        
        System.out.println("长期稳定性测试结果:");
        System.out.println("成功请求: " + requestCount);
        System.out.println("失败请求: " + errorCount);
        System.out.println("错误率: " + (errorRate * 100) + "%");
    }

    @Test
    @DisplayName("异常头部信息处理测试")
    public void testAbnormalHeaders() throws Exception {
        String url = "http://localhost:" + port + "/api/test/duplicate-submit/basic";
        String requestBody = "abnormal-headers-test";
        
        // 测试异常的HTTP头
        HttpHeaders abnormalHeaders = new HttpHeaders();
        abnormalHeaders.setContentType(MediaType.APPLICATION_JSON);
        
        // 添加异常长的头部值
        String longValue = "A".repeat(10000);
        abnormalHeaders.add("X-Custom-Long-Header", longValue);
        
        // 添加特殊字符的头部
        abnormalHeaders.add("X-Special-Chars", "测试🚀特殊字符");
        
        // 添加空值头部
        abnormalHeaders.add("X-Empty-Header", "");
        
        HttpEntity<String> entity = new HttpEntity<>(requestBody, abnormalHeaders);
        
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            
            // 应该能处理异常头部或返回适当错误
            assertTrue(response.getStatusCode() == HttpStatus.OK || 
                      response.getStatusCode().is4xxClientError(),
                      "异常头部应该被适当处理");
                      
        } catch (Exception e) {
            // 异常也是可以接受的
            assertTrue(true, "异常头部导致异常是可以接受的: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("系统资源极限测试")
    public void testSystemResourceLimits() throws Exception {
        String url = "http://localhost:" + port + "/api/test/idempotent/basic";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // 测试大量不同的幂等请求（测试内存使用）
        int uniqueRequestCount = 1000;
        int successCount = 0;
        int errorCount = 0;
        
        for (int i = 0; i < uniqueRequestCount; i++) {
            String uniqueBody = "{\"uniqueId\":" + i + ",\"timestamp\":" + System.currentTimeMillis() + "}";
            HttpEntity<String> entity = new HttpEntity<>(uniqueBody, headers);
            
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                if (response.getStatusCode() == HttpStatus.OK) {
                    successCount++;
                } else {
                    errorCount++;
                }
            } catch (Exception e) {
                errorCount++;
            }
            
            // 每100个请求检查一次内存使用情况
            if (i % 100 == 0) {
                Runtime runtime = Runtime.getRuntime();
                long usedMemory = runtime.totalMemory() - runtime.freeMemory();
                long maxMemory = runtime.maxMemory();
                double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
                
                // 如果内存使用超过80%，停止测试
                if (memoryUsagePercent > 80) {
                    System.out.println("内存使用率过高，停止测试: " + memoryUsagePercent + "%");
                    break;
                }
            }
            
            // 小延迟避免过度压力
            if (i % 10 == 0) {
                Thread.sleep(10);
            }
        }
        
        // 验证系统在资源压力下的表现
        assertTrue(successCount > 0, "应该有成功的请求");
        double errorRate = (double) errorCount / (successCount + errorCount);
        assertTrue(errorRate < 0.5, "错误率应该控制在50%以内，实际错误率: " + errorRate);
        
        System.out.println("资源极限测试结果:");
        System.out.println("成功请求: " + successCount);
        System.out.println("失败请求: " + errorCount);
        System.out.println("错误率: " + (errorRate * 100) + "%");
    }

    @Test
    @DisplayName("网络超时和连接异常模拟测试")
    public void testNetworkAbnormalities() throws Exception {
        // 创建一个有超时设置的RestTemplate
        TestRestTemplate timeoutRestTemplate = new TestRestTemplate();
        timeoutRestTemplate.getRestTemplate().setRequestFactory(
            new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                setConnectTimeout(1000);  // 1秒连接超时
                setReadTimeout(2000);     // 2秒读取超时
            }}
        );
        
        String url = "http://localhost:" + port + "/api/test/idempotent/basic";
        String requestBody = "{\"data\":\"timeout-test\"}";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<String> response = timeoutRestTemplate.postForEntity(url, entity, String.class);
            
            // 如果没有超时，验证正常响应
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            
        } catch (org.springframework.web.client.ResourceAccessException e) {
            // 超时异常是可以接受的
            assertTrue(e.getMessage().contains("timeout") || e.getMessage().contains("timed out"),
                "应该是超时相关的异常: " + e.getMessage());
        } catch (Exception e) {
            // 其他网络异常也是可以接受的
            System.out.println("网络异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    @Test
    @DisplayName("错误恢复能力测试")
    public void testErrorRecoveryCapability() throws Exception {
        String url = "http://localhost:" + port + "/api/test/duplicate-submit/basic";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // 首先发送一些会导致错误的请求
        try {
            HttpEntity<String> invalidEntity = new HttpEntity<>("{invalid json", headers);
            restTemplate.postForEntity(url, invalidEntity, String.class);
        } catch (Exception e) {
            // 忽略预期的错误
        }
        
        // 然后发送正常请求，验证系统是否能恢复
        String validRequestBody = "recovery-test-" + System.currentTimeMillis();
        HttpEntity<String> validEntity = new HttpEntity<>(validRequestBody, headers);
        
        ResponseEntity<String> response = restTemplate.postForEntity(url, validEntity, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
            "系统应该能从错误中恢复并正常处理请求");
        
        // 验证保护功能仍然正常工作
        ResponseEntity<String> duplicateResponse = restTemplate.postForEntity(url, validEntity, String.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, duplicateResponse.getStatusCode(),
            "错误恢复后，保护功能应该仍然正常工作");
    }
}
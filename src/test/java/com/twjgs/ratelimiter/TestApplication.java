package com.twjgs.ratelimiter;

import com.twjgs.ratelimiter.annotation.MultiRateLimit;
import com.twjgs.ratelimiter.annotation.RateLimit;
import com.twjgs.ratelimiter.annotation.DuplicateSubmit;
import com.twjgs.ratelimiter.annotation.Idempotent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test application to demonstrate Smart Rate Limiter usage
 * 
 * @author Smart Rate Limiter Team
 * @since 1.0.0
 */
@SpringBootApplication
@Import(TestConfiguration.class)
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    @RestController
    public static class TestController {

        /**
         * Basic rate limiting example
         */
        @GetMapping("/api/basic")
        @RateLimit(permits = 10, window = 60)
        public String basicRateLimit() {
            return "Basic rate limit: 10 requests per minute";
        }

        /**
         * IP-based rate limiting
         */
        @GetMapping("/api/ip-limited")
        @RateLimit(
            dimension = RateLimit.LimitDimension.IP,
            permits = 5,
            window = 30,
            message = "Too many requests from your IP"
        )
        public String ipRateLimit() {
            return "IP-based rate limit: 5 requests per 30 seconds per IP";
        }

        /**
         * User-based rate limiting
         */
        @GetMapping("/api/user-limited")
        @RateLimit(
            dimension = RateLimit.LimitDimension.USER,
            permits = 3,
            window = 60,
            algorithm = RateLimit.LimitAlgorithm.TOKEN_BUCKET,
            bucketCapacity = 5,
            refillRate = 0.1
        )
        public String userRateLimit() {
            return "User-based rate limit with token bucket algorithm";
        }

        /**
         * Custom key expression
         */
        @GetMapping("/api/custom")
        @RateLimit(
            dimension = RateLimit.LimitDimension.CUSTOM,
            permits = 20,
            window = 60,
            keyExpression = "#ip + ':' + #headers.get('User-Agent')",
            message = "Rate limit based on IP and User-Agent"
        )
        public String customRateLimit() {
            return "Custom rate limit using IP + User-Agent combination";
        }

        /**
         * Multi-dimensional rate limiting
         */
        @GetMapping("/api/multi")
        @MultiRateLimit({
            @RateLimit(dimension = RateLimit.LimitDimension.IP, permits = 100, window = 60),
            @RateLimit(dimension = RateLimit.LimitDimension.USER, permits = 10, window = 60),
            @RateLimit(dimension = RateLimit.LimitDimension.GLOBAL, permits = 1000, window = 60)
        })
        public String multiRateLimit() {
            return "Multi-dimensional rate limiting: IP (100/min) AND User (10/min) AND Global (1000/min)";
        }

        /**
         * Queue strategy example
         */
        @GetMapping("/api/queue")
        @RateLimit(
            permits = 2,
            window = 10,
            strategy = RateLimit.LimitStrategy.QUEUE,
            queueTimeout = 2000,
            message = "Request queued due to rate limit"
        )
        public String queueStrategy() {
            return "Queue strategy: wait up to 2 seconds if rate limited";
        }

        /**
         * Different algorithms demonstration
         */
        @GetMapping("/api/sliding-window")
        @RateLimit(permits = 5, window = 30, algorithm = RateLimit.LimitAlgorithm.SLIDING_WINDOW)
        public String slidingWindow() {
            return "Sliding window algorithm - most precise";
        }

        @GetMapping("/api/fixed-window")
        @RateLimit(permits = 5, window = 30, algorithm = RateLimit.LimitAlgorithm.FIXED_WINDOW)
        public String fixedWindow() {
            return "Fixed window algorithm - best performance";
        }

        @GetMapping("/api/token-bucket")
        @RateLimit(
            permits = 5, 
            window = 30, 
            algorithm = RateLimit.LimitAlgorithm.TOKEN_BUCKET,
            bucketCapacity = 10,
            refillRate = 0.2
        )
        public String tokenBucket() {
            return "Token bucket algorithm - allows bursts";
        }

        @GetMapping("/api/leaky-bucket")
        @RateLimit(permits = 5, window = 30, algorithm = RateLimit.LimitAlgorithm.LEAKY_BUCKET)
        public String leakyBucket() {
            return "Leaky bucket algorithm - smooth rate enforcement";
        }

        /**
         * No rate limit for comparison
         */
        @GetMapping("/api/unlimited")
        public String unlimited() {
            return "No rate limiting applied";
        }

        // ====== API Protection Suite Examples ======
        
        /**
         * Basic duplicate submit prevention
         */
        @PostMapping("/api/comment")
        @DuplicateSubmit
        public String addComment(@RequestBody(required = false) String content) {
            return "Comment added: " + (content != null ? content : "empty") + " at " + System.currentTimeMillis();
        }

        /**
         * Custom interval duplicate submit
         */
        @PostMapping("/api/like")
        @DuplicateSubmit(
            interval = 10,
            message = "点赞操作过于频繁，请10秒后重试"
        )
        public String addLike(@RequestBody(required = false) String data) {
            return "Like added at " + System.currentTimeMillis();
        }

        /**
         * IP-based duplicate submit
         */
        @PostMapping("/api/feedback")
        @DuplicateSubmit(
            interval = 60,
            dimension = DuplicateSubmit.KeyDimension.IP_METHOD,
            message = "该IP操作过于频繁"
        )
        public String submitFeedback(@RequestBody(required = false) String feedback) {
            return "Feedback submitted at " + System.currentTimeMillis();
        }

        /**
         * Global method duplicate submit
         */
        @PostMapping("/api/system-config")
        @DuplicateSubmit(
            interval = 30,
            dimension = DuplicateSubmit.KeyDimension.GLOBAL_METHOD,
            message = "系统繁忙，请稍后重试"
        )
        public String updateSystemConfig(@RequestBody(required = false) String config) {
            return "System config updated at " + System.currentTimeMillis();
        }

        /**
         * Custom key expression duplicate submit
         */
        @PostMapping("/api/order")
        @DuplicateSubmit(
            interval = 300,
            dimension = DuplicateSubmit.KeyDimension.CUSTOM,
            keyExpression = "'order:' + #request.getParameter('productId')",
            message = "请勿重复提交相同商品的订单"
        )
        public String createOrder(@RequestBody(required = false) String orderData) {
            return "Order created at " + System.currentTimeMillis();
        }

        /**
         * Basic idempotent operation
         */
        @PostMapping("/api/idempotent/create")
        @Idempotent(timeout = 300)
        public String idempotentCreate(@RequestBody(required = false) String data) {
            // Simulate processing time
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "Resource created at " + System.currentTimeMillis();
        }

        /**
         * Custom key idempotent operation
         */
        @PostMapping("/api/idempotent/payment")
        @Idempotent(
            timeout = 600,
            keyStrategy = Idempotent.KeyStrategy.CUSTOM,
            keyExpression = "'payment:' + #request.getParameter('orderId')",
            message = "订单正在处理中，请勿重复提交"
        )
        public String processPayment(@RequestBody(required = false) String paymentData) {
            // Simulate payment processing
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "Payment processed at " + System.currentTimeMillis();
        }
    }
}
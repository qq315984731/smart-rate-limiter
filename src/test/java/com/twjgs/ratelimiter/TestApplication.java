package com.twjgs.ratelimiter;

import com.twjgs.ratelimiter.annotation.MultiRateLimit;
import com.twjgs.ratelimiter.annotation.RateLimit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test application to demonstrate Smart Rate Limiter usage
 * 
 * @author Smart Rate Limiter Team
 * @since 1.0.0
 */
@SpringBootApplication
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
    }
}
package com.urlshortener.common.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimitingService {

    private final StringRedisTemplate redisTemplate;

    // Rate limit configuration: 60 requests per 1 minute (60,000 milliseconds)
    private static final int MAX_REQUESTS_PER_WINDOW = 60;
    private static final long WINDOW_SIZE_IN_MS = 60000;

    @Autowired
    public RateLimitingService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Checks if the given client IP has exceeded the rate limit using a sliding window algorithm.
     * 
     * @param clientIp The IP address of the client making the request.
     * @return true if the request is allowed, false if the rate limit is exceeded.
     */
    public boolean allowRequest(String clientIp) {
        return allowRequest(clientIp, "global", MAX_REQUESTS_PER_WINDOW, WINDOW_SIZE_IN_MS);
    }

    public boolean allowRequest(String clientIp, String endpointId, int maxRequests, long windowSizeInMs) {
        String key = "rate_limit:" + endpointId + ":" + clientIp;
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - windowSizeInMs;

        // 1. Remove timestamps older than the sliding window
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

        // 2. Count the remaining requests in the current window
        Long currentRequests = redisTemplate.opsForZSet().zCard(key);

        if (currentRequests != null && currentRequests >= maxRequests) {
            // Rate limit exceeded
            return false;
        }

        // 3. Add the current request timestamp to the sorted set
        // We append a UUID to ensure the value is unique (since ZSETs require unique members)
        String value = currentTime + "-" + UUID.randomUUID().toString();
        redisTemplate.opsForZSet().add(key, value, currentTime);

        // 4. Set an expiry on the key so it cleans itself up if the IP stops making requests
        redisTemplate.expire(key, windowSizeInMs, TimeUnit.MILLISECONDS);

        return true;
    }
}

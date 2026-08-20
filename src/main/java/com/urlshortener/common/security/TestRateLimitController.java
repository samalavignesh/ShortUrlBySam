package com.urlshortener.common.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test-rate-limit")
public class TestRateLimitController {

    @GetMapping
    public String testRateLimit() {
        return "Success! You are within the rate limit.";
    }
}

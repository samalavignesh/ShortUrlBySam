package com.urlshortener.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitingService rateLimitingService;

    @Autowired
    public RateLimitInterceptor(RateLimitingService rateLimitingService) {
        this.rateLimitingService = rateLimitingService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Extract client IP address
        String clientIp = getClientIp(request);
        String uri = request.getRequestURI();

        boolean allowed = true;
        if (uri.startsWith("/api/auth/send-registration-otp") || uri.startsWith("/api/auth/forgot-password")) {
            // 5 requests per 15 minutes for OTPs
            allowed = rateLimitingService.allowRequest(clientIp, "otp", 5, 15 * 60 * 1000);
        } else if (uri.startsWith("/api/auth/login")) {
            // 10 requests per 5 minutes for Login
            allowed = rateLimitingService.allowRequest(clientIp, "login", 10, 5 * 60 * 1000);
        } else {
            // Global default limit
            allowed = rateLimitingService.allowRequest(clientIp);
        }

        // Check if the request is allowed
        if (!allowed) {
            // Rate limit exceeded: return 429 Too Many Requests
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Too many requests. Please try again later.");
            return false; // Stop further processing
        }

        return true; // Allow the request to proceed
    }

    /**
     * Extracts the real client IP from the request, accounting for proxies (e.g., Nginx, AWS ELB).
     */
    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}

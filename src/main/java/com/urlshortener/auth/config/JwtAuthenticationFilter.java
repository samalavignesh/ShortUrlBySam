package com.urlshortener.auth.config;

import com.urlshortener.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * =====================================================
 * JWT AUTHENTICATION FILTER
 * =====================================================
 *
 * This filter intercepts EVERY HTTP request and checks for a valid
 * JWT token. If found, it authenticates the user for that request.
 *
 * WHAT IS A FILTER?
 * ─────────────────
 * Filters are components in the Servlet API that intercept HTTP
 * requests BEFORE they reach your controller. Think of them as
 * security checkpoints at an airport:
 *
 *   Request → [Filter 1] → [Filter 2] → [Filter 3] → Controller
 *                 ↑
 *          JwtAuthenticationFilter sits here
 *
 * WHY OncePerRequestFilter?
 * ─────────────────────────
 * Regular filters might execute MULTIPLE TIMES per request
 * (e.g., during forwards or includes). OncePerRequestFilter
 * guarantees our JWT check runs exactly ONCE per request.
 * This prevents duplicate authentication and improves performance.
 *
 * THE COMPLETE REQUEST LIFECYCLE (With JWT):
 * ──────────────────────────────────────────
 *
 * ┌─────────┐     HTTP Request     ┌─────────────────────────┐
 * │ Client  │ ──────────────────→  │ JwtAuthenticationFilter  │
 * │         │  Authorization:      │                          │
 * │         │  Bearer eyJhbG...    │  1. Extract token        │
 * └─────────┘                      │  2. Validate token       │
 *                                  │  3. Load UserDetails     │
 *                                  │  4. Set authentication   │
 *                                  └────────────┬─────────────┘
 *                                               │
 *                                               ▼
 *                                  ┌─────────────────────────┐
 *                                  │  SecurityFilterChain     │
 *                                  │  (more security checks)  │
 *                                  └────────────┬─────────────┘
 *                                               │
 *                                               ▼
 *                                  ┌─────────────────────────┐
 *                                  │  Controller              │
 *                                  │  (processes request)     │
 *                                  └─────────────────────────┘
 *
 * WHAT HAPPENS IF THERE'S NO TOKEN?
 * ──────────────────────────────────
 * The filter simply passes the request to the next filter WITHOUT
 * setting authentication. Later, Spring Security's authorization
 * check will see "no authentication" and either:
 *   - Allow it (if the endpoint is public, like /api/auth/login)
 *   - Reject it with 401 (if the endpoint requires authentication)
 *
 * @Component → Registers this as a Spring-managed bean so it can be
 *              injected into the SecurityFilterChain configuration.
 *
 * @RequiredArgsConstructor → Generates constructor for final fields
 *                            (JwtService and UserDetailsService).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /*
     * JwtService — handles token parsing, validation, and username extraction.
     */
    private final JwtService jwtService;

    /*
     * UserDetailsService — loads user data from the database.
     * We use the interface type (not CustomUserDetailsService) for
     * loose coupling. Spring injects our implementation automatically.
     */
    private final UserDetailsService userDetailsService;

    /*
     * ===== THE MAIN FILTER METHOD =====
     *
     * This method runs for EVERY HTTP request. It decides whether
     * the request has a valid JWT and, if so, authenticates the user.
     *
     * PARAMETERS:
     *   request     → The incoming HTTP request (has headers, URL, etc.)
     *   response    → The HTTP response (we don't modify it here)
     *   filterChain → The chain of remaining filters to execute after us
     *
     * @NonNull annotations tell the compiler these parameters are never null.
     * This prevents NullPointerException warnings in the IDE.
     *
     * ALGORITHM:
     * ┌──────────────────────────────────────────────────┐
     * │ 1. Get Authorization header                      │
     * │ 2. No header or not "Bearer "? → SKIP (pass on) │
     * │ 3. Extract token (remove "Bearer " prefix)       │
     * │ 4. Extract username from token                   │
     * │ 5. Username null? → SKIP                        │
     * │ 6. Already authenticated? → SKIP                │
     * │ 7. Load UserDetails from database                │
     * │ 8. Token valid for this user? → SET AUTH         │
     * │ 9. Continue to next filter                       │
     * └──────────────────────────────────────────────────┘
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        /*
         * STEP 1: Extract the Authorization header.
         *
         * HTTP headers look like:
         *   GET /api/urls HTTP/1.1
         *   Host: localhost:8080
         *   Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
         *   Content-Type: application/json
         *
         * We're looking for the "Authorization" header.
         */
        final String authHeader = request.getHeader("Authorization");

        /*
         * STEP 2: Validate the header format.
         *
         * The header MUST:
         *   - Exist (not null)
         *   - Start with "Bearer " (note the space after "Bearer")
         *
         * WHY "Bearer"?
         *   → It's the standard scheme for JWT-based authentication,
         *     defined in RFC 6750. Other schemes include:
         *     - "Basic" (for username:password base64)
         *     - "Digest" (hash-based)
         *     - "Bearer" (token-based — that's us!)
         *
         * If the header is missing or uses a different scheme,
         * we DON'T authenticate and pass the request to the next filter.
         * This allows public endpoints to work without tokens.
         */
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        /*
         * STEP 3: Extract the raw JWT token.
         *
         * "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIi..."
         *          ↑ starts at index 7
         *
         * substring(7) removes the "Bearer " prefix (7 characters).
         */
        final String jwt = authHeader.substring(7);

        /*
         * STEP 4: Extract the username from the token.
         *
         * This calls JwtService.extractUsername() which:
         * 1. Parses the token
         * 2. Verifies the signature
         * 3. Returns the "sub" (subject) claim
         *
         * If the token is malformed, expired, or has an invalid signature,
         * the JJWT library throws an exception. We let it propagate —
         * Spring Security will handle it as an authentication failure.
         */
        try {
            final String username = jwtService.extractUsername(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // If token is invalid, malformed, or expired, we just ignore it.
            // Spring Security will handle the lack of authentication downstream.
        }

        /*
         * STEP 9: Continue the filter chain.
         *
         * filterChain.doFilter() passes the request to the NEXT filter
         * in the chain. Eventually, it reaches the DispatcherServlet
         * which routes it to the appropriate controller.
         *
         * THIS MUST ALWAYS BE CALLED! If you forget this line,
         * the request will hang and never reach the controller.
         * Even if authentication fails, we call this — the security
         * framework handles authorization later.
         */
        filterChain.doFilter(request, response);
    }
}

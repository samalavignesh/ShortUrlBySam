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
        final String username = jwtService.extractUsername(jwt);

        /*
         * STEP 5 & 6: Check if we should authenticate.
         *
         * We only proceed if:
         * (a) We successfully extracted a username (not null)
         * (b) The user is NOT already authenticated for this request
         *
         * WHY CHECK SecurityContextHolder?
         *   → In some cases, a previous filter might have already
         *     authenticated the user. No need to do it again.
         *
         * SecurityContextHolder is a THREAD-LOCAL storage that holds
         * the current user's authentication for the duration of the request.
         * After the request completes, it's cleared automatically.
         *
         * .getContext().getAuthentication() returns:
         *   - null → No one is authenticated yet (first time)
         *   - An Authentication object → Already authenticated
         */
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            /*
             * STEP 7: Load the full UserDetails from the database.
             *
             * We need the full UserDetails (password hash, roles) to:
             * 1. Validate the token against the current user state
             * 2. Set up the authentication with the correct authorities
             *
             * NOTE: This hits the database on EVERY authenticated request.
             * In a high-traffic app, you'd cache UserDetails in Redis
             * to avoid the DB round-trip. We'll add that optimization later.
             */
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

            /*
             * STEP 8: Validate the token.
             *
             * isTokenValid() checks:
             * 1. Does the token's username match the loaded user?
             * 2. Is the token NOT expired?
             *
             * Both must be true to proceed.
             */
            if (jwtService.isTokenValid(jwt, userDetails)) {

                /*
                 * STEP 8a: Create an Authentication token.
                 *
                 * UsernamePasswordAuthenticationToken is Spring Security's
                 * standard authentication object. Despite the name, it's
                 * used for ANY authenticated user (not just username/password).
                 *
                 * Constructor parameters:
                 *   userDetails   → The authenticated user (principal)
                 *   null          → Credentials (null because we already verified via JWT)
                 *   authorities   → The user's roles/permissions (e.g., ROLE_USER)
                 *
                 * WHY null FOR CREDENTIALS?
                 *   → The password was already verified during login.
                 *     For subsequent requests, the JWT itself IS the credential.
                 *     We don't need (or want) the password floating around.
                 */
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                /*
                 * STEP 8b: Attach request details to the authentication.
                 *
                 * WebAuthenticationDetailsSource creates a details object
                 * that includes the remote IP address and session ID.
                 * This is used for:
                 *   - Audit logging ("who accessed what from where?")
                 *   - Security analysis (detecting suspicious IPs)
                 *   - Session management
                 *
                 * It's optional but considered best practice.
                 */
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                /*
                 * STEP 8c: Set the authentication in the SecurityContext.
                 *
                 * THIS IS THE KEY STEP — it tells Spring Security:
                 * "This request is authenticated as user X with roles Y."
                 *
                 * From this point on, for the rest of this request:
                 *   - @AuthenticationPrincipal returns this UserDetails
                 *   - hasRole("USER") checks pass if user has ROLE_USER
                 *   - SecurityContextHolder.getContext().getAuthentication()
                 *     returns this auth token
                 *
                 * After the request completes, Spring clears the context
                 * (because we're stateless — no sessions).
                 */
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
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

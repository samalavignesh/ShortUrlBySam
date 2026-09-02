package com.urlshortener.auth.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * =====================================================
 * SECURITY CONFIGURATION
 * =====================================================
 *
 * This is the CENTRAL security configuration for the entire application.
 * It defines WHO can access WHAT, and HOW authentication works.
 *
 * Think of this class as the BLUEPRINT for your application's security:
 *
 * ┌────────────────────────────────────────────────────────────────────┐
 * │ SECURITY ARCHITECTURE │
 * ├────────────────────────────────────────────────────────────────────┤
 * │ │
 * │ HTTP Request │
 * │ │ │
 * │ ▼ │
 * │ ┌──────────────────────┐ │
 * │ │ CSRF Filter (OFF) │ We disable CSRF for REST APIs │
 * │ └──────────┬───────────┘ │
 * │ ▼ │
 * │ ┌──────────────────────┐ │
 * │ │ JWT Auth Filter │ Extracts & validates Bearer token │
 * │ └──────────┬───────────┘ │
 * │ ▼ │
 * │ ┌──────────────────────┐ │
 * │ │ Authorization Rules │ Checks URL patterns & roles │
 * │ │ │ │
 * │ │ /api/auth/** → ALL │ Public (login, register) │
 * │ │ /swagger-ui/** → ALL│ Public (API docs) │
 * │ │ GET /{code} → ALL │ Public (URL redirect) │
 * │ │ Everything else │ AUTHENTICATED only │
 * │ └──────────┬───────────┘ │
 * │ ▼ │
 * │ ┌──────────────────────┐ │
 * │ │ Controller │ Request reaches your business logic │
 * │ └──────────────────────┘ │
 * │ │
 * └────────────────────────────────────────────────────────────────────┘
 *
 * KEY ANNOTATIONS EXPLAINED:
 * ──────────────────────────
 *
 * @Configuration
 *                → Tells Spring: "This class contains @Bean methods that define
 *                Spring-managed objects." Spring calls these methods at startup
 *                and registers the returned objects in the application context.
 *
 * @EnableWebSecurity
 *                    → Activates Spring Security's web security features.
 *                    Without this, none of the security configuration takes
 *                    effect.
 *                    It imports the SecurityFilterChain infrastructure.
 *
 * @EnableMethodSecurity
 *                       → Enables method-level security annotations like:
 *                       @PreAuthorize("hasRole('ADMIN')") → Check BEFORE method
 *                       runs
 *                       @PostAuthorize(...) → Check AFTER method runs
 *                       @Secured("ROLE_ADMIN") → Simpler role check
 *
 *                       This lets us protect individual SERVICE METHODS, not
 *                       just URLs.
 *                       Example: A service method that only admins can call.
 *
 * @RequiredArgsConstructor
 *                          → Lombok generates constructor for final fields.
 *                          Spring injects JwtAuthenticationFilter and
 *                          UserDetailsService.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /*
     * Our custom JWT filter — injected by Spring.
     * We'll insert it into the security filter chain.
     */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /*
     * Our CustomUserDetailsService — injected by Spring.
     * Used to configure the AuthenticationProvider.
     */
    private final UserDetailsService userDetailsService;

    // ================================================================
    // BEAN DEFINITIONS — Objects managed by Spring's container
    // ================================================================

    /*
     * ===== SECURITY FILTER CHAIN =====
     *
     * This is the MOST IMPORTANT bean in this class. It defines:
     * 1. Which endpoints are public vs protected
     * 2. How sessions are managed (stateless for JWT)
     * 3. Where our JWT filter sits in the filter chain
     * 4. Which authentication provider to use
     *
     * @Bean → Tells Spring to call this method and manage the returned
     * object. Other components can then @Autowire it.
     *
     * HttpSecurity is a BUILDER that configures web-based security
     * for specific HTTP requests. Think of it as writing security
     * rules in code instead of XML.
     *
     * IMPORTANT CONCEPT — THE FILTER CHAIN:
     * ──────────────────────────────────────
     * Spring Security processes requests through a chain of filters.
     * Each filter does ONE thing. The order matters!
     *
     * Default chain (simplified):
     * SecurityContextFilter → CsrfFilter → LogoutFilter →
     * UsernamePasswordAuthenticationFilter → ExceptionTranslationFilter →
     * AuthorizationFilter
     *
     * We INSERT our JwtAuthenticationFilter BEFORE the default
     * UsernamePasswordAuthenticationFilter. This way, JWT auth
     * happens first, and if successful, the default filter is skipped.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(org.springframework.security.config.Customizer.withDefaults())
                /*
                 * CSRF PROTECTION — DISABLED
                 *
                 * CSRF (Cross-Site Request Forgery) protection prevents
                 * malicious websites from making requests on behalf of
                 * logged-in users.
                 *
                 * WHY DISABLE IT?
                 * → CSRF protection uses cookies and hidden form tokens.
                 * Our API uses JWT in the Authorization HEADER instead.
                 * Since browsers don't automatically send custom headers
                 * (unlike cookies), CSRF attacks are not possible.
                 *
                 * RULE OF THUMB:
                 * - Cookie-based auth → NEED CSRF protection
                 * - Header-based auth (JWT) → DON'T need CSRF
                 *
                 * AbstractHttpConfigurer::disable is a method reference
                 * that calls csrf.disable(). It's equivalent to:
                 * .csrf(csrf -> csrf.disable())
                 */
                .csrf(AbstractHttpConfigurer::disable)

                /*
                 * FRAME OPTIONS — DISABLED (for H2 Console)
                 *
                 * The H2 Console uses iframes internally. By default,
                 * Spring Security blocks iframes (clickjacking protection).
                 * We disable this so the H2 Console works during development.
                 *
                 * In PRODUCTION with PostgreSQL, remove this line.
                 */
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))

                /*
                 * URL-BASED AUTHORIZATION RULES
                 *
                 * This section defines which URLs are public and which
                 * require authentication. Rules are evaluated IN ORDER
                 * — the FIRST matching rule wins.
                 *
                 * .requestMatchers(...).permitAll()
                 * → "Anyone can access these URLs, no token needed."
                 *
                 * .anyRequest().authenticated()
                 * → "Everything else requires a valid JWT token."
                 */
                .authorizeHttpRequests(auth -> auth

                        /*
                         * PUBLIC ENDPOINTS — No authentication required
                         *
                         * /api/auth/** → Login and registration endpoints.
                         * Obviously, you can't require a token to LOG IN
                         * (that's a chicken-and-egg problem!).
                         *
                         * The ** wildcard matches any sub-path:
                         * /api/auth/login → ✅ matches
                         * /api/auth/register → ✅ matches
                         * /api/auth/forgot-password → ✅ matches
                         */
                        .requestMatchers("/api/auth/**").permitAll()

                        /*
                         * SWAGGER UI — Public access for API documentation
                         *
                         * These paths serve the Swagger/OpenAPI documentation:
                         * /swagger-ui/** → The Swagger UI web interface
                         * /v3/api-docs/** → The OpenAPI JSON specification
                         * /swagger-ui.html → Alternative entry point
                         *
                         * We make these public so developers can explore
                         * the API without needing a token first.
                         */
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/h2-console/**",
                                "/api/health")
                        .permitAll()

                        /*
                         * URL REDIRECT — Public access for short URL redirection
                         *
                         * GET /{shortCode} → Redirects to the original URL.
                         * This MUST be public! The whole point of a URL
                         * shortener is that ANYONE can click the short link.
                         *
                         * HttpMethod.GET restricts this to GET requests only.
                         * POST/PUT/DELETE to /{something} still require auth.
                         *
                         * The pattern "/{shortCode}" uses a path variable.
                         * Spring matches any single path segment:
                         * GET /abc123 → ✅ matches (shortCode = "abc123")
                         * GET /my-link → ✅ matches (shortCode = "my-link")
                         * GET /api/urls → ❌ doesn't match (two segments)
                         */
                        .requestMatchers(HttpMethod.GET, "/{shortCode}").permitAll()

                        /*
                         * CATCH-ALL — Everything else requires authentication
                         *
                         * Any URL not matched above requires a valid JWT token.
                         * This includes:
                         * POST /api/urls → Create a short URL (need to know WHO)
                         * GET /api/urls → List user's URLs (need to know WHO)
                         * DELETE /api/urls/{id} → Delete a URL (need auth + ownership)
                         * GET /api/urls/{id}/analytics → View analytics (need auth)
                         */
                        .anyRequest().authenticated())

                /*
                 * SESSION MANAGEMENT — STATELESS
                 *
                 * SessionCreationPolicy.STATELESS tells Spring Security:
                 * "NEVER create or use HTTP sessions."
                 *
                 * WHY STATELESS?
                 * → With JWT, the token carries all user info.
                 * We don't need the server to remember anything
                 * between requests. Each request is self-contained.
                 *
                 * BENEFITS OF STATELESS:
                 * ┌────────────────────────────────────────────┐
                 * │ 1. Scalability: No session replication │
                 * │ needed across multiple servers │
                 * │ 2. Memory: No session objects stored │
                 * │ on the server │
                 * │ 3. Simplicity: No session timeout, │
                 * │ invalidation, or cleanup needed │
                 * │ 4. REST compliance: True stateless API │
                 * └────────────────────────────────────────────┘
                 *
                 * WITHOUT THIS: Spring creates an HttpSession for each
                 * user, stores the SecurityContext in the session, and
                 * uses JSESSIONID cookies. That defeats the purpose of JWT.
                 */
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                /*
                 * AUTHENTICATION PROVIDER
                 *
                 * Registers our DaoAuthenticationProvider (defined below)
                 * as the component that handles username/password authentication.
                 *
                 * This tells Spring Security:
                 * "When someone tries to authenticate, use THIS provider
                 * to load the user and verify the password."
                 */
                .authenticationProvider(authenticationProvider())

                /*
                 * INSERT JWT FILTER
                 *
                 * This is where we plug our JwtAuthenticationFilter into
                 * Spring Security's filter chain.
                 *
                 * .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                 * means: "Run our JWT filter BEFORE the default login filter."
                 *
                 * WHY BEFORE?
                 * → If our JWT filter successfully authenticates the user,
                 * the default UsernamePasswordAuthenticationFilter sees
                 * that authentication already exists and skips its logic.
                 * This prevents double authentication.
                 *
                 * Filter execution order after this:
                 * ... → JwtAuthenticationFilter → UsernamePasswordAuthFilter → ...
                 */
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        /*
         * .build() finalizes the HttpSecurity configuration and creates
         * the SecurityFilterChain object that Spring Security uses.
         */
        return http.build();
    }

    /*
     * ===== PASSWORD ENCODER =====
     *
     * BCryptPasswordEncoder hashes passwords using the BCrypt algorithm.
     *
     * WHAT IS BCrypt?
     * ───────────────
     * BCrypt is a password-hashing function designed to be SLOW.
     * Yes, slow on PURPOSE! Here's why:
     *
     * ┌──────────────────────────────────────────────────────────┐
     * │ Algorithm │ Speed │ Time to crack 1B hashes │
     * ├──────────────────────────────────────────────────────────┤
     * │ MD5 │ 10B hashes/sec │ ~0.1 seconds │
     * │ SHA-256 │ 5B hashes/sec │ ~0.2 seconds │
     * │ BCrypt │ 15K hashes/sec │ ~2 years │
     * └──────────────────────────────────────────────────────────┘
     *
     * BCrypt uses a "cost factor" (default 10) that controls how
     * many rounds of hashing are performed (2^10 = 1024 rounds).
     * Each increase of 1 DOUBLES the time.
     *
     * HOW BCrypt HASHING WORKS:
     * Password: "myPassword123"
     * ↓
     * BCrypt generates random SALT: "$2a$10$N9qo8uLOickgx2ZMRZoMye"
     * ↓
     * Hash = BCrypt(password + salt, 10 rounds)
     * ↓
     * Stored: "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
     *
     * The salt is EMBEDDED in the hash string, so each password
     * produces a DIFFERENT hash even with the same input.
     * This defeats rainbow table attacks.
     *
     * @Bean makes this available for injection anywhere we need it
     * (like the AuthService for registration, or DaoAuthenticationProvider).
     */
    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        configuration.setAllowedOrigins(java.util.List.of(
            "http://localhost:5173",
            "http://localhost:3000",
            "http://shorturlbysam.duckdns.org",
            "https://shorturlbysam.duckdns.org"
        ));
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(java.util.List.of("*"));
        configuration.setAllowCredentials(true);

        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /*
     * ===== AUTHENTICATION PROVIDER =====
     *
     * DaoAuthenticationProvider is Spring Security's standard provider
     * for authenticating with a database (DAO = Data Access Object).
     *
     * It handles the login flow:
     * 1. Receives username + password from the login request
     * 2. Calls UserDetailsService.loadUserByUsername(username)
     * → This loads the user from our database
     * 3. Uses PasswordEncoder.matches(rawPassword, hashedPassword)
     * → Compares the submitted password with the stored hash
     * 4. If match → Returns authenticated Authentication object
     * If no match → Throws BadCredentialsException
     *
     * WHY DO WE CONFIGURE THIS EXPLICITLY?
     * ─────────────────────────────────────
     * Spring Security needs to know TWO things to authenticate users:
     * 1. WHERE to find user data → UserDetailsService
     * 2. HOW to verify passwords → PasswordEncoder
     *
     * By creating this bean, we connect these two pieces together.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /*
     * ===== AUTHENTICATION MANAGER =====
     *
     * The AuthenticationManager is the ENTRY POINT for authentication.
     * When our AuthService wants to authenticate a user (during login),
     * it calls authenticationManager.authenticate(credentials).
     *
     * Spring Boot auto-configures an AuthenticationManager, but we
     * need to expose it as a @Bean so we can inject it into our
     * AuthService.
     *
     * AuthenticationConfiguration is Spring's auto-config class that
     * builds the AuthenticationManager from all registered
     * AuthenticationProviders (including our DaoAuthenticationProvider).
     *
     * USAGE IN AuthService:
     * authenticationManager.authenticate(
     * new UsernamePasswordAuthenticationToken(username, password)
     * );
     * // If this doesn't throw → credentials are valid!
     * // If it throws BadCredentialsException → wrong password
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

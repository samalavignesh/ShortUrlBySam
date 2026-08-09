package com.urlshortener.auth.controller;

import com.urlshortener.auth.service.AuthService;
import com.urlshortener.dto.request.LoginRequest;
import com.urlshortener.dto.request.RegisterRequest;
import com.urlshortener.dto.response.ApiResponse;
import com.urlshortener.dto.response.AuthResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * =====================================================
 * AUTH CONTROLLER
 * =====================================================
 *
 * This is the REST API entry point for authentication.
 * It exposes two endpoints that clients call to register
 * new accounts and log in to existing ones.
 *
 * ENDPOINTS:
 * ──────────
 * POST /api/auth/register → Create a new user account
 * POST /api/auth/login    → Authenticate and get a JWT token
 *
 * WHERE DOES THIS FIT IN THE ARCHITECTURE?
 * ─────────────────────────────────────────
 *
 * ┌─────────┐     HTTP      ┌────────────────┐    Business    ┌─────────────┐
 * │ Client  │ ────────────→ │ AuthController  │ ────────────→ │ AuthService  │
 * │(Browser,│  JSON Request │  (this class)   │    Logic      │             │
 * │ Mobile, │               │                 │               │ register()  │
 * │ Postman)│ ←──────────── │ Handles HTTP    │ ←──────────── │ login()     │
 * └─────────┘  JSON Response│ concerns ONLY   │  Returns DTO  └─────────────┘
 *                           └────────────────┘
 *
 * CONTROLLER RESPONSIBILITIES (and what it does NOT do):
 * ──────────────────────────────────────────────────────
 *
 * ✅ DOES:
 *   - Receive HTTP requests and deserialize JSON to DTOs
 *   - Trigger input validation (@Valid)
 *   - Call the appropriate service method
 *   - Wrap the result in ApiResponse
 *   - Set the correct HTTP status code
 *   - Return the response as JSON
 *
 * ❌ DOES NOT:
 *   - Contain business logic (that's AuthService's job)
 *   - Access the database directly (that's Repository's job)
 *   - Handle exceptions (that's GlobalExceptionHandler's job)
 *   - Hash passwords or generate tokens (that's Service's job)
 *
 * WHY THIS SEPARATION?
 * ────────────────────
 * If you put everything in the controller (DB access, validation,
 * business rules, error handling), you get a "God class" — a massive,
 * untestable, unmaintainable mess. By separating concerns:
 *
 * 1. TESTABILITY: You can unit test the service without HTTP
 * 2. REUSABILITY: The service can be called from other places
 *    (e.g., a scheduled job, another service, an admin tool)
 * 3. READABILITY: Each class is small and focused
 * 4. MAINTAINABILITY: Changes in one layer don't affect others
 *
 * KEY ANNOTATIONS:
 * ────────────────
 *
 * @RestController
 *   → Combines @Controller + @ResponseBody.
 *     @Controller: Marks this as a Spring MVC controller.
 *     @ResponseBody: All methods return data directly (as JSON),
 *       not a view template name. Without @ResponseBody, Spring
 *       would try to find a Thymeleaf/JSP template named "register".
 *
 * @RequestMapping("/api/auth")
 *   → Sets the BASE PATH for all endpoints in this controller.
 *     All methods' paths are RELATIVE to this base:
 *       @PostMapping("/register") → POST /api/auth/register
 *       @PostMapping("/login")    → POST /api/auth/login
 *
 *   WHY "/api/auth"?
 *   → The "/api" prefix distinguishes API endpoints from static resources.
 *   → The "/auth" segment groups all authentication-related endpoints.
 *   → This path is whitelisted in SecurityConfig as permitAll()
 *     so these endpoints don't require a JWT token.
 *
 * @RequiredArgsConstructor
 *   → Lombok generates a constructor for the 'final' AuthService field.
 *     Spring auto-injects the AuthService bean via this constructor.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /*
     * AuthService — injected by Spring via constructor injection.
     * This is our ONLY dependency. The controller delegates ALL
     * business logic to this service.
     */
    private final AuthService authService;

    /*
     * ===== REGISTER ENDPOINT =====
     *
     * POST /api/auth/register
     *
     * Creates a new user account and returns a JWT token
     * for immediate authentication (auto-login after registration).
     *
     * REQUEST:
     * ────────
     * POST /api/auth/register
     * Content-Type: application/json
     *
     * {
     *   "username": "john_doe",
     *   "email": "john@example.com",
     *   "password": "securePassword123"
     * }
     *
     * SUCCESS RESPONSE (201 Created):
     * ────────────────────────────────
     * {
     *   "success": true,
     *   "message": "User registered successfully",
     *   "data": {
     *     "token": "eyJhbGciOiJIUzI1NiJ9...",
     *     "username": "john_doe",
     *     "role": "USER"
     *   },
     *   "timestamp": "2026-08-10T00:30:00"
     * }
     *
     * ERROR RESPONSES:
     * ────────────────
     * 400 Bad Request → Validation failed (missing fields, invalid email, etc.)
     * {
     *   "success": false,
     *   "message": "Validation failed",
     *   "data": { "username": "Username is required", "email": "Please provide a valid email" }
     * }
     *
     * 409 Conflict → Username or email already taken
     * {
     *   "success": false,
     *   "message": "User already exists with username: john_doe"
     * }
     *
     * ANNOTATIONS EXPLAINED:
     * ──────────────────────
     *
     * @PostMapping("/register")
     *   → Maps HTTP POST requests to /api/auth/register to this method.
     *     POST is the correct HTTP method for CREATING resources.
     *     GET would be wrong — registration has side effects (creates a user).
     *
     * @RequestBody
     *   → Tells Spring to deserialize the request JSON body into a
     *     RegisterRequest object. Spring uses Jackson (JSON library)
     *     to map JSON fields to Java fields by name.
     *
     *     JSON: { "username": "john" }  →  registerRequest.getUsername() = "john"
     *
     *     Without @RequestBody, Spring would try to read the data from
     *     URL parameters (?username=john), not the request body.
     *
     * @Valid
     *   → Triggers Jakarta Bean Validation on the RegisterRequest.
     *     Spring checks ALL validation annotations (@NotBlank, @Size, @Email)
     *     BEFORE calling this method.
     *
     *     If validation fails, Spring throws MethodArgumentNotValidException
     *     which our GlobalExceptionHandler catches and returns as 400.
     *
     *     The method body NEVER executes with invalid data. This is the
     *     "fail fast" principle — reject bad input at the gate.
     *
     * WHY RETURN HTTP 201 (Created) INSTEAD OF 200 (OK)?
     * ───────────────────────────────────────────────────
     * HTTP semantics matter:
     *   200 OK → "I did what you asked" (generic success)
     *   201 Created → "I created a NEW resource" (more specific)
     *
     * Since registration CREATES a new user, 201 is semantically correct.
     * It tells the client (and intermediaries like caches/proxies) that
     * a new resource now exists on the server.
     *
     * ResponseEntity<ApiResponse<AuthResponse>>
     *   → ResponseEntity lets us set the HTTP status code.
     *     ApiResponse wraps the response in our standard format.
     *     AuthResponse is the actual payload (token, username, role).
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        AuthResponse authResponse = authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", authResponse));
    }

    /*
     * ===== LOGIN ENDPOINT =====
     *
     * POST /api/auth/login
     *
     * Authenticates a user and returns a JWT token.
     *
     * REQUEST:
     * ────────
     * POST /api/auth/login
     * Content-Type: application/json
     *
     * {
     *   "username": "john_doe",
     *   "password": "securePassword123"
     * }
     *
     * SUCCESS RESPONSE (200 OK):
     * ──────────────────────────
     * {
     *   "success": true,
     *   "message": "Login successful",
     *   "data": {
     *     "token": "eyJhbGciOiJIUzI1NiJ9...",
     *     "username": "john_doe",
     *     "role": "USER"
     *   },
     *   "timestamp": "2026-08-10T00:30:00"
     * }
     *
     * ERROR RESPONSE (401 Unauthorized):
     * ───────────────────────────────────
     * {
     *   "success": false,
     *   "message": "Invalid username or password"
     * }
     *
     * WHY 200 FOR LOGIN (NOT 201)?
     * ────────────────────────────
     * Login doesn't CREATE a resource — it VERIFIES credentials
     * and generates a token. 200 OK is the correct status.
     *
     * SECURITY NOTE:
     * ──────────────
     * - We return the SAME error for wrong username AND wrong password
     * - This prevents attackers from discovering valid usernames
     * - The generic "Invalid username or password" message is intentional
     * - See AuthService.login() and GlobalExceptionHandler for details
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse authResponse = authService.login(request);

        return ResponseEntity
                .ok(ApiResponse.success("Login successful", authResponse));
    }
}

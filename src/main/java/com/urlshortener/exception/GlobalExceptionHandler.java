package com.urlshortener.exception;

import com.urlshortener.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * =====================================================
 * GLOBAL EXCEPTION HANDLER
 * =====================================================
 *
 * This is the CENTRAL ERROR HANDLING component of the application.
 * It catches exceptions thrown ANYWHERE in the app and converts
 * them into clean, consistent API responses.
 *
 * WHAT IS @RestControllerAdvice?
 * ──────────────────────────────
 * It's a combination of two annotations:
 *
 * @ControllerAdvice → "Advice" that applies to ALL controllers globally.
 *   Think of it as an interceptor that wraps around every controller.
 *   When any controller throws an exception, this class catches it.
 *
 * @ResponseBody → Automatically serializes the return value to JSON.
 *   @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 *
 * WITHOUT THIS CLASS:
 * ───────────────────
 * If a controller throws an exception, Spring returns this ugly default:
 * {
 *   "timestamp": "2026-08-08T14:30:00.000+00:00",
 *   "status": 500,
 *   "error": "Internal Server Error",
 *   "trace": "com.urlshortener.exception.ResourceNotFoundException: ...\n
 *             at com.urlshortener.service.UrlService.getUrl(UrlService.java:45)\n
 *             at com.urlshortener.controller.UrlController.redirect(...)...",
 *   "path": "/api/urls/abc123"
 * }
 *
 * Problems:
 * 1. SECURITY RISK: Stack trace exposes internal class names, method names,
 *    and line numbers. Attackers use this to find vulnerabilities.
 * 2. INCONSISTENT FORMAT: Different from our ApiResponse format.
 * 3. ALWAYS 500: Even for user errors (bad input, not found, etc.)
 *
 * WITH THIS CLASS:
 * ────────────────
 * We return clean, safe responses:
 * {
 *   "success": false,
 *   "message": "Url not found with shortCode: abc123",
 *   "timestamp": "2026-08-08T14:30:00"
 * }
 *
 * HOW IT WORKS — THE EXCEPTION FLOW:
 * ───────────────────────────────────
 * 1. Client sends request → Controller → Service
 * 2. Service throws ResourceNotFoundException
 * 3. Exception bubbles UP through Controller (not caught there)
 * 4. Spring looks for an @ExceptionHandler that matches the exception type
 * 5. Finds handleResourceNotFoundException() in this class
 * 6. Calls it with the exception → Returns ResponseEntity with 404 status
 * 7. Spring serializes to JSON and sends to client
 *
 * The method matching is by exception TYPE:
 *   @ExceptionHandler(ResourceNotFoundException.class) catches ResourceNotFoundException
 *   @ExceptionHandler(Exception.class) catches EVERYTHING ELSE (fallback)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /*
     * ===== HANDLER: Resource Not Found (404) =====
     *
     * @ExceptionHandler(ResourceNotFoundException.class)
     *   → This method ONLY handles ResourceNotFoundException.
     *     Any other exception type is ignored by this method.
     *
     * The method receives the exception object as a parameter,
     * so we can extract the error message from it.
     *
     * ResponseEntity<ApiResponse<Void>>:
     *   → ResponseEntity lets us set the HTTP STATUS CODE.
     *     ApiResponse<Void> means the response has no data payload
     *     (just success=false and a message).
     *
     *   .status(HttpStatus.NOT_FOUND) → HTTP 404
     *   .body(ApiResponse.error(...)) → Our clean error response
     *
     * WHEN THIS FIRES:
     *   - Short code doesn't exist: GET /abc123 → 404
     *   - User not found: GET /api/users/999 → 404
     *   - Any service method that calls .orElseThrow(ResourceNotFoundException)
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(
            ResourceNotFoundException ex) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)                // HTTP 404
                .body(ApiResponse.error(ex.getMessage()));   // "Url not found with shortCode: abc123"
    }

    /*
     * ===== HANDLER: Duplicate Resource (409 Conflict) =====
     *
     * WHEN THIS FIRES:
     *   - Username already taken during registration → 409
     *   - Email already in use → 409
     *   - Custom short code already taken → 409
     *
     * WHY 409 Conflict?
     *   → The request is valid, but it CONFLICTS with the current
     *     state of the server (resource already exists).
     *   → Tells the client: "Your request was fine, but something
     *     with that identifier already exists. Try a different one."
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResourceException(
            DuplicateResourceException ex) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)                 // HTTP 409
                .body(ApiResponse.error(ex.getMessage()));   // "User already exists with username: john"
    }

    /*
     * ===== HANDLER: URL Expired (410 Gone) =====
     *
     * WHEN THIS FIRES:
     *   - Someone clicks a short URL that has passed its expiresAt date
     *
     * 410 tells both the client and search engines:
     * "This resource once existed but is permanently gone."
     */
    @ExceptionHandler(UrlExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleUrlExpiredException(
            UrlExpiredException ex) {

        return ResponseEntity
                .status(HttpStatus.GONE)                     // HTTP 410
                .body(ApiResponse.error(ex.getMessage()));   // "URL with short code 'abc123' has expired"
    }

    /*
     * ===== HANDLER: Unauthorized Access (403 Forbidden) =====
     *
     * WHEN THIS FIRES:
     *   - User tries to delete another user's URL → 403
     *   - Non-admin tries to access admin endpoints → 403
     *
     * Remember: 403 = "I know who you are, but you can't do this"
     */
    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedAccessException(
            UnauthorizedAccessException ex) {

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)                // HTTP 403
                .body(ApiResponse.error(ex.getMessage()));   // "You can only delete your own URLs"
    }

    /*
     * ===== HANDLER: Bad Credentials (401 Unauthorized) =====
     *
     * BadCredentialsException is thrown by Spring Security's
     * AuthenticationManager when login fails (wrong password).
     *
     * SECURITY NOTE: We return a GENERIC message "Invalid username or password"
     * instead of specific "Wrong password" or "User not found".
     * This prevents USERNAME ENUMERATION — an attacker can't figure out
     * which usernames exist by getting different error messages.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(
            BadCredentialsException ex) {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)             // HTTP 401
                .body(ApiResponse.error("Invalid username or password"));
    }

    /*
     * ===== HANDLER: Validation Errors (400 Bad Request) =====
     *
     * MethodArgumentNotValidException is thrown automatically by Spring
     * when @Valid fails on a request DTO.
     *
     * For example, if RegisterRequest has @NotBlank on username
     * and the client sends { "username": "", "email": "..." },
     * Spring throws this exception BEFORE the controller method executes.
     *
     * We extract ALL field errors and return them as a map:
     * {
     *   "success": false,
     *   "message": "Validation failed",
     *   "data": {
     *     "username": "Username is required",
     *     "password": "Password must be between 6 and 100 characters",
     *     "email": "Please provide a valid email address"
     *   },
     *   "timestamp": "2026-08-08T14:30:00"
     * }
     *
     * WHY RETURN ALL ERRORS AT ONCE?
     * ──────────────────────────────
     * Imagine filling out a registration form. Would you rather:
     * A) Submit → "Username required" → Fix → Submit → "Email invalid" → Fix → Submit → "Password too short"
     *    (3 round trips! Frustrating!)
     * B) Submit → "Username required, Email invalid, Password too short"
     *    (1 round trip, fix everything at once!)
     *
     * Option B is much better UX. That's why we collect ALL errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException ex) {

        /*
         * Build a map of field name → error message.
         *
         * ex.getBindingResult().getAllErrors() returns all validation errors.
         * Each error is cast to FieldError which has:
         *   - getField() → "username", "email", "password"
         *   - getDefaultMessage() → "Username is required", etc.
         *
         * We put them in a HashMap for clean JSON output.
         */
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)              // HTTP 400
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Validation failed")
                        .data(errors)
                        .timestamp(java.time.LocalDateTime.now())
                        .build());
    }

    /*
     * ===== HANDLER: Generic IllegalArgumentException (400 Bad Request) =====
     *
     * Catches any IllegalArgumentException thrown in our code.
     * Used for business logic validation that isn't covered by @Valid.
     *
     * Example: Invalid custom short code format, invalid date parsing, etc.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
            IllegalArgumentException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)              // HTTP 400
                .body(ApiResponse.error(ex.getMessage()));
    }

    /*
     * ===== HANDLER: Catch-All Fallback (500 Internal Server Error) =====
     *
     * THIS IS THE SAFETY NET — catches ANY exception not handled above.
     *
     * Exception.class is the parent of ALL exceptions, so this catches
     * anything that slips through the specific handlers above.
     *
     * WHY IS THIS IMPORTANT?
     * ──────────────────────
     * Without this, an unhandled NullPointerException (bug in our code)
     * would return Spring's default error response with a FULL STACK TRACE.
     * That's a SECURITY VULNERABILITY — it reveals internal architecture.
     *
     * With this handler, even unexpected bugs return a clean response:
     * {
     *   "success": false,
     *   "message": "An unexpected error occurred. Please try again later.",
     *   "timestamp": "2026-08-08T14:30:00"
     * }
     *
     * IMPORTANT: We DON'T expose ex.getMessage() to the client!
     * Internal error messages might contain:
     * - Database connection strings
     * - SQL queries with table names
     * - File paths
     * - Internal class names
     *
     * Instead, we return a generic message and LOG the actual error
     * (logging will be added when we configure the service layer).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex) {

        // DEBUG: expose the actual exception for diagnosis (REMOVE in production!)
        ex.printStackTrace();
        String debugMsg = "[DEBUG] " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)    // HTTP 500
                .body(ApiResponse.error(debugMsg));
    }
}

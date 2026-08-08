package com.urlshortener.exception;

/**
 * =====================================================
 * UNAUTHORIZED ACCESS EXCEPTION
 * =====================================================
 *
 * Thrown when a user tries to perform an action they don't
 * have permission for.
 *
 * IMPORTANT DISTINCTION — Authentication vs Authorization:
 * ────────────────────────────────────────────────────────
 * AUTHENTICATION = "Who are you?" (login, JWT validation)
 *   → Handled by Spring Security filters BEFORE reaching our code
 *   → Returns 401 Unauthorized automatically
 *
 * AUTHORIZATION = "Are you ALLOWED to do this?" (permission check)
 *   → Handled IN our code (service layer)
 *   → This exception covers this case
 *   → Returns 403 Forbidden
 *
 * USE CASES:
 * ──────────
 * 1. User A tries to delete User B's URL:
 *    if (!url.getUser().getId().equals(currentUser.getId())) {
 *        throw new UnauthorizedAccessException("You can only delete your own URLs");
 *    }
 *
 * 2. Regular USER tries to access admin-only analytics:
 *    if (currentUser.getRole() != Role.ADMIN) {
 *        throw new UnauthorizedAccessException("Admin access required");
 *    }
 *
 * WHY 403 (Forbidden) AND NOT 401 (Unauthorized)?
 * ────────────────────────────────────────────────
 * Despite the confusing naming:
 * - 401 = "You're not logged in" (authentication failure)
 * - 403 = "You're logged in but not allowed" (authorization failure)
 *
 * A 401 says "try logging in". A 403 says "even with your credentials,
 * you can't do this". Very different meanings!
 *
 * HTTP RESULT: 403 Forbidden (mapped in GlobalExceptionHandler)
 */
public class UnauthorizedAccessException extends RuntimeException {

    public UnauthorizedAccessException(String message) {
        super(message);
    }
}

package com.urlshortener.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * =====================================================
 * AUTH RESPONSE DTO
 * =====================================================
 *
 * Returned to the client after successful login or registration.
 * Contains the JWT token they'll use for all subsequent requests.
 *
 * RESPONSE JSON EXAMPLE:
 * {
 *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJqb2huX...",
 *   "username": "john_doe",
 *   "role": "USER"
 * }
 *
 * HOW THE CLIENT USES THIS:
 * ─────────────────────────
 * 1. Client receives this response after login
 * 2. Stores the token (in localStorage, cookie, or memory)
 * 3. For every subsequent request, adds the token in the header:
 *    Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
 * 4. Server validates the token on every request
 * 5. If token is valid → Request proceeds
 *    If token is expired/invalid → 401 Unauthorized
 *
 * WHY INCLUDE username AND role?
 * ──────────────────────────────
 * The frontend often needs to know WHO is logged in and WHAT ROLE
 * they have, immediately after login, without making another API call.
 * - username → Display "Welcome, john_doe!" in the UI
 * - role → Show/hide admin-only features
 *
 * The token ALSO contains this info (encoded inside), but the
 * frontend would have to decode the JWT to read it.
 * Including it directly is more convenient.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    /*
     * The JWT token string.
     *
     * A JWT has 3 parts separated by dots:
     * header.payload.signature
     *
     * Example: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huIiwiZXhwIjoxNjk...".eyJhbGciOi..."
     *
     * - Header: Algorithm used (HS256) + token type (JWT)
     * - Payload: User data (username, role, expiration time)
     * - Signature: Cryptographic proof that the token wasn't tampered with
     *
     * The token is NOT encrypted — anyone can decode the payload.
     * But only the server can CREATE a valid token (using the secret key).
     * If anyone modifies the payload, the signature won't match → REJECTED.
     */
    private String token;

    /* The authenticated user's username — for UI display */
    private String username;

    /* The user's role — for frontend authorization decisions */
    private String role;
}

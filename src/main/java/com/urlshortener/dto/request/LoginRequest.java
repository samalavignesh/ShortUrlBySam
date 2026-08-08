package com.urlshortener.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * =====================================================
 * LOGIN REQUEST DTO
 * =====================================================
 *
 * Carries the credentials a user submits to log in.
 * Simpler than RegisterRequest — we only need username + password.
 *
 * REQUEST JSON EXAMPLE:
 * {
 *   "username": "john_doe",
 *   "password": "securePassword123"
 * }
 *
 * AUTHENTICATION FLOW:
 * ────────────────────
 * 1. Client sends this DTO to POST /api/auth/login
 * 2. AuthService finds the user by username
 * 3. BCrypt compares the raw password with the stored hash
 * 4. If match → Generate JWT token → Return AuthResponse
 * 5. If no match → Throw "Invalid credentials" exception
 *
 * WHY NO @Email OR @Size HERE?
 * ────────────────────────────
 * For login, we only need @NotBlank. There's no need to validate
 * format because:
 * - If the username doesn't exist → "Invalid credentials" (same error)
 * - If the password is wrong → "Invalid credentials" (same error)
 *
 * We intentionally return the SAME error for both cases.
 * This prevents "username enumeration" attacks where an attacker
 * can discover valid usernames by getting different error messages
 * for "user not found" vs "wrong password".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}

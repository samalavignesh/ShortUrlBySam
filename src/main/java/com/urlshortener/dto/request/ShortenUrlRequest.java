package com.urlshortener.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * =====================================================
 * SHORTEN URL REQUEST DTO
 * =====================================================
 *
 * Carries the data needed to create a new shortened URL.
 *
 * REQUEST JSON EXAMPLES:
 *
 * Minimal (only required field):
 * {
 *   "originalUrl": "https://www.example.com/very/long/path?param=value"
 * }
 *
 * With optional custom short code:
 * {
 *   "originalUrl": "https://www.example.com/very/long/path",
 *   "customCode": "my-link"
 * }
 *
 * With optional expiration (ISO 8601 format):
 * {
 *   "originalUrl": "https://www.example.com/very/long/path",
 *   "expiresAt": "2027-01-01T00:00:00"
 * }
 *
 * URL SHORTENING FLOW:
 * ────────────────────
 * 1. Client sends this DTO to POST /api/urls/shorten
 * 2. Service validates the original URL
 * 3. If customCode provided → Check if it's available
 *    If not provided → Generate a random 6-8 char code
 * 4. Save the Url entity to the database
 * 5. Return UrlResponse with the short URL
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortenUrlRequest {

    /*
     * @NotBlank → The original URL is mandatory. Can't shorten nothing!
     *
     * @URL → Hibernate Validator annotation (not JPA).
     *   Validates that the string is a properly formatted URL.
     *   Valid: "https://www.google.com", "http://localhost:8080/test"
     *   Invalid: "not a url", "ftp://weird-protocol", ""
     *
     *   WHY VALIDATE THE URL?
     *   Without validation, users could shorten:
     *   - "javascript:alert('hacked')" → XSS attack via redirect
     *   - Random text → Redirect to nowhere
     *   - Malware URLs → Your shortener becomes a malware distributor
     *
     *   @URL ensures only valid HTTP/HTTPS URLs are accepted.
     */
    @NotBlank(message = "Original URL is required")
    @URL(message = "Please provide a valid URL")
    private String originalUrl;

    /*
     * CUSTOM SHORT CODE — Optional.
     *
     * If the user wants a specific short code (vanity URL) instead
     * of a random one. For example: "my-brand" → localhost:8080/my-brand
     *
     * No @NotBlank → This field is OPTIONAL. If null or empty,
     * the service will generate a random short code.
     *
     * The service layer will validate:
     * - Length (must be 3-20 characters)
     * - Format (alphanumeric and hyphens only)
     * - Availability (not already taken)
     */
    @Size(min = 3, max = 20, message = "Custom code must be between 3 and 20 characters")
    @Pattern(regexp = "^[a-zA-Z0-9-]+$", message = "Custom code can only contain letters, numbers, and hyphens")
    private String customCode;

    /*
     * EXPIRATION — Optional.
     *
     * String instead of LocalDateTime because:
     * 1. JSON doesn't have a native date type
     * 2. Different clients may send dates in different formats
     * 3. We parse and validate it in the service layer
     *
     * Expected format: ISO 8601 ("2027-01-01T00:00:00")
     * If null → URL never expires.
     */
    private String expiresAt;
}

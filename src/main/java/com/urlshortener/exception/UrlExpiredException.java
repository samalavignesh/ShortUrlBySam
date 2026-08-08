package com.urlshortener.exception;

/**
 * =====================================================
 * URL EXPIRED EXCEPTION
 * =====================================================
 *
 * Thrown when someone tries to access a short URL that has
 * passed its expiration date.
 *
 * SCENARIO:
 * ─────────
 * 1. User creates a short URL with expiresAt = "2026-09-01"
 * 2. On September 2nd, someone clicks the short URL
 * 3. System checks: expiresAt (Sep 1) < now (Sep 2) → EXPIRED!
 * 4. Throws UrlExpiredException
 * 5. GlobalExceptionHandler returns HTTP 410 Gone
 *
 * WHY HTTP 410 (Gone) AND NOT 404 (Not Found)?
 * ─────────────────────────────────────────────
 * - 404 means "this resource never existed or we can't find it"
 * - 410 means "this resource EXISTED but is no longer available"
 *
 * 410 is semantically correct — the URL was valid, it just expired.
 * It also tells search engines to stop indexing this link,
 * whereas 404 might cause them to retry later.
 *
 * HTTP RESULT: 410 Gone (mapped in GlobalExceptionHandler)
 */
public class UrlExpiredException extends RuntimeException {

    private final String shortCode;

    public UrlExpiredException(String shortCode) {
        super(String.format("URL with short code '%s' has expired", shortCode));
        this.shortCode = shortCode;
    }

    public String getShortCode() { return shortCode; }
}

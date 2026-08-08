package com.urlshortener.enums;

/**
 * =====================================================
 * ROLE ENUM
 * =====================================================
 *
 * Defines the authorization roles in our application.
 * Spring Security uses these to decide what a user can access.
 *
 * Why an Enum instead of a String?
 * ─────────────────────────────────
 * 1. TYPE SAFETY: You can't accidentally type "ADMNI" instead of "ADMIN"
 *    — the compiler catches it.
 * 2. AUTOCOMPLETE: Your IDE suggests valid values.
 * 3. REFACTORING: If you rename a role, the compiler finds every usage.
 * 4. DOCUMENTATION: The enum itself serves as documentation of all
 *    possible roles in the system.
 *
 * How it maps to the database:
 * ────────────────────────────
 * By default, JPA stores enums as integers (0, 1, 2...).
 * But we use @Enumerated(EnumType.STRING) on the User entity
 * so it stores "USER" or "ADMIN" as readable text in the DB.
 * This is much safer — if you reorder the enum values, the DB
 * data doesn't break.
 */
public enum Role {

    /**
     * USER role — Standard user who can:
     *  - Create shortened URLs
     *  - View their own URLs and analytics
     *  - Delete their own URLs
     */
    USER,

    /**
     * ADMIN role — Administrator who can:
     *  - Everything a USER can do
     *  - View all users' URLs and analytics
     *  - Delete any URL
     *  - Manage user accounts
     */
    ADMIN
}

package com.urlshortener.exception;

/**
 * =====================================================
 * DUPLICATE RESOURCE EXCEPTION
 * =====================================================
 *
 * Thrown when trying to create a resource that already exists.
 *
 * USE CASES:
 * ──────────
 * 1. Registration with an existing username:
 *    throw new DuplicateResourceException("User", "username", "john_doe");
 *    → "User already exists with username: john_doe"
 *
 * 2. Registration with an existing email:
 *    throw new DuplicateResourceException("User", "email", "john@example.com");
 *    → "User already exists with email: john@example.com"
 *
 * 3. Custom short code that's already taken:
 *    throw new DuplicateResourceException("Url", "shortCode", "my-link");
 *    → "Url already exists with shortCode: my-link"
 *
 * WHY NOT JUST CATCH THE DATABASE UNIQUE CONSTRAINT VIOLATION?
 * ────────────────────────────────────────────────────────────
 * We COULD let the save() call fail and catch DataIntegrityViolationException.
 * But that approach has problems:
 *
 * 1. UNCLEAR MESSAGE: The DB exception says something like
 *    "ERROR: duplicate key value violates unique constraint 'users_username_key'"
 *    That's not user-friendly!
 *
 * 2. HARD TO DIFFERENTIATE: Was it the username or email that was duplicate?
 *    The DB exception doesn't tell you clearly.
 *
 * 3. LATE FAILURE: We've already done password hashing and other processing
 *    before the save() call. Checking first with existsBy...() is faster.
 *
 * By checking BEFORE the save and throwing this specific exception,
 * we give the user a clear, actionable error message.
 *
 * HTTP RESULT: 409 Conflict (mapped in GlobalExceptionHandler)
 */
public class DuplicateResourceException extends RuntimeException {

    private final String resourceName;
    private final String fieldName;
    private final Object fieldValue;

    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s already exists with %s: %s", resourceName, fieldName, fieldValue));
        this.resourceName = resourceName;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    public String getResourceName() { return resourceName; }
    public String getFieldName() { return fieldName; }
    public Object getFieldValue() { return fieldValue; }
}

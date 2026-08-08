package com.urlshortener.exception;

/**
 * =====================================================
 * RESOURCE NOT FOUND EXCEPTION
 * =====================================================
 *
 * Thrown when a requested resource doesn't exist in the database.
 *
 * WHAT IS A CUSTOM EXCEPTION?
 * ───────────────────────────
 * Java has built-in exceptions (NullPointerException, IllegalArgumentException),
 * but they're too generic. Custom exceptions:
 *
 * 1. DESCRIBE THE PROBLEM: "ResourceNotFoundException" is much clearer
 *    than "RuntimeException" — you immediately know what went wrong.
 *
 * 2. ENABLE SPECIFIC HANDLING: The GlobalExceptionHandler can catch
 *    ResourceNotFoundException separately and return HTTP 404,
 *    while catching other exceptions as HTTP 500.
 *
 * 3. CARRY CONTEXT: We store the resource name, field, and value
 *    so the error message is specific:
 *    "Url not found with shortCode: abc123"
 *    instead of just "Not found"
 *
 * WHY EXTEND RuntimeException (unchecked) INSTEAD OF Exception (checked)?
 * ────────────────────────────────────────────────────────────────────────
 * Checked exceptions (extends Exception):
 *   - FORCE every caller to either catch or declare throws
 *   - Pollute method signatures: void myMethod() throws MyException
 *   - Make code verbose and harder to read
 *
 * Unchecked exceptions (extends RuntimeException):
 *   - Don't force callers to handle them
 *   - Bubble up naturally to the GlobalExceptionHandler
 *   - Cleaner, more modern Java style
 *   - Spring Boot prefers this approach
 *
 * USAGE EXAMPLES:
 *   throw new ResourceNotFoundException("Url", "shortCode", "abc123");
 *   → Message: "Url not found with shortCode: abc123"
 *
 *   throw new ResourceNotFoundException("User", "id", 42);
 *   → Message: "User not found with id: 42"
 *
 * HTTP RESULT: 404 Not Found (mapped in GlobalExceptionHandler)
 */
public class ResourceNotFoundException extends RuntimeException {

    /*
     * These fields store context about WHICH resource was not found.
     * They're useful for logging and debugging.
     *
     * Example:
     *   resourceName = "Url"
     *   fieldName = "shortCode"
     *   fieldValue = "abc123"
     */
    private final String resourceName;
    private final String fieldName;
    private final Object fieldValue;

    /**
     * Constructor that builds a descriptive error message.
     *
     * @param resourceName The type of resource (e.g., "Url", "User")
     * @param fieldName    The field used to search (e.g., "shortCode", "id")
     * @param fieldValue   The value that was searched for (e.g., "abc123", 42)
     *
     * super(...) → Passes the message to RuntimeException's constructor.
     * String.format() → Builds the message by inserting values into the template.
     *   %s = string placeholder → replaced by the arguments in order.
     */
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: %s", resourceName, fieldName, fieldValue));
        this.resourceName = resourceName;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    public String getResourceName() { return resourceName; }
    public String getFieldName() { return fieldName; }
    public Object getFieldValue() { return fieldValue; }
}

package com.urlshortener.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * =====================================================
 * JWT SERVICE
 * =====================================================
 *
 * This service handles ALL operations related to JSON Web Tokens (JWT).
 * It's the brain behind our token-based authentication system.
 *
 * WHAT IS JWT?
 * ────────────
 * JWT (JSON Web Token) is a compact, URL-safe token format used to
 * securely transmit information between parties. Think of it as a
 * digitally signed ID card:
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                        JWT STRUCTURE                            │
 * ├──────────────┬──────────────────┬──────────────────────────────┤
 * │   HEADER     │     PAYLOAD      │         SIGNATURE            │
 * │  (Algorithm) │   (User Data)    │   (Tamper-proof Seal)        │
 * ├──────────────┼──────────────────┼──────────────────────────────┤
 * │ {            │ {                │ HMACSHA256(                  │
 * │  "alg":"HS256"│  "sub":"john",  │   base64(header) + "." +    │
 * │  "typ":"JWT" │  "role":"USER",  │   base64(payload),          │
 * │ }            │  "exp":17200...  │   secret_key                 │
 * │              │ }                │ )                            │
 * └──────────────┴──────────────────┴──────────────────────────────┘
 *        ↓               ↓                     ↓
 *   eyJhbGci...   .  eyJzdWIi...    .    SflKxwRJSM...
 *
 * These 3 parts are Base64-encoded and joined with dots.
 * The result looks like: xxxxx.yyyyy.zzzzz
 *
 * WHY JWT INSTEAD OF SESSIONS?
 * ────────────────────────────
 * Traditional sessions store user data on the SERVER (in memory or DB).
 * JWTs store user data IN THE TOKEN ITSELF (on the client side).
 *
 * ┌──────────────────────┬──────────────────────────────────────┐
 * │     Sessions         │              JWT                     │
 * ├──────────────────────┼──────────────────────────────────────┤
 * │ Server stores state  │ Server is STATELESS (no storage)    │
 * │ Needs sticky sessions│ Any server can validate the token   │
 * │ Hard to scale        │ Easy to scale horizontally          │
 * │ Session ID in cookie │ Token in Authorization header       │
 * │ Server lookup needed │ Token contains all info needed      │
 * └──────────────────────┴──────────────────────────────────────┘
 *
 * SECURITY FLOW:
 * ──────────────
 * 1. User logs in with username + password
 * 2. Server validates credentials
 * 3. Server creates a JWT, signs it with SECRET_KEY
 * 4. Server sends JWT to the client
 * 5. Client stores JWT (localStorage, cookie, etc.)
 * 6. Client sends JWT in every request header:
 *    Authorization: Bearer eyJhbGci...
 * 7. Server extracts JWT, validates signature, reads user data
 * 8. If valid → Process request. If invalid → 401 Unauthorized.
 *
 * THE SECRET KEY IS CRITICAL:
 * ───────────────────────────
 * - Only the server knows the secret key
 * - The secret key signs every token
 * - If someone steals your secret key, they can forge ANY token
 * - NEVER commit the secret key to Git!
 * - Store it in environment variables or a vault
 */
@Service
public class JwtService {

    /*
     * @Value("${jwt.secret}")
     *   → Injects the value of 'jwt.secret' from application.properties.
     *     This is our HMAC signing key (Base64-encoded).
     *
     * WHY NOT HARDCODE IT?
     *   → Hardcoding secrets in source code is a CRITICAL security flaw.
     *     Anyone with access to the code (Git repo, decompiled JAR)
     *     could forge valid tokens and impersonate any user.
     *     By externalizing it, we can use different keys per environment
     *     (dev/staging/prod) and rotate keys without code changes.
     */
    @Value("${jwt.secret}")
    private String secretKey;

    /*
     * @Value("${jwt.expiration}")
     *   → Token expiration time in MILLISECONDS.
     *     Default: 86400000 ms = 24 hours.
     *
     * WHY DO TOKENS EXPIRE?
     *   → If a token is stolen, the damage is limited to the expiration window.
     *     Without expiration, a stolen token would work FOREVER.
     *
     * ┌────────────────────────────────┐
     * │  Common Expiration Strategies  │
     * ├────────────────────────────────┤
     * │  15 minutes → Very secure     │
     * │  1 hour → Balanced            │
     * │  24 hours → Convenient        │
     * │  7 days → Remember me feature │
     * └────────────────────────────────┘
     *
     * We use 24 hours as a balance between security and convenience.
     */
    @Value("${jwt.expiration}")
    private long jwtExpiration;

    // ================================================================
    // PUBLIC METHODS — Called by other services and the JWT filter
    // ================================================================

    /*
     * ===== EXTRACT USERNAME FROM TOKEN =====
     *
     * Opens the token, reads the "sub" (subject) claim, and returns it.
     * The "sub" claim is a standard JWT claim that holds the principal
     * (in our case, the username).
     *
     * This is called by JwtAuthenticationFilter on EVERY request
     * to identify WHO is making the request.
     *
     * Flow:
     *   Token: "eyJhbGci..." → Parse → Claims → subject = "john_doe"
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /*
     * ===== GENERATE TOKEN (Simple) =====
     *
     * Creates a JWT token for the given user with NO extra claims.
     * This is the method called after successful login/registration.
     *
     * Delegates to the overloaded method with an empty claims map.
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    /*
     * ===== GENERATE TOKEN (With Extra Claims) =====
     *
     * Creates a JWT token with optional extra data in the payload.
     *
     * Parameters:
     *   extraClaims → Additional data to embed in the token.
     *                  Example: {"role": "ADMIN", "userId": 42}
     *   userDetails → Spring Security's user object (has username, authorities)
     *
     * The token contains:
     * ┌────────────────────────────────────────────────────────┐
     * │  PAYLOAD                                               │
     * ├──────────────┬─────────────────────────────────────────┤
     * │  sub         │  "john_doe"  (from userDetails)         │
     * │  role        │  "ADMIN"     (from extraClaims)         │
     * │  iat         │  1691500000  (issued at - current time) │
     * │  exp         │  1691586400  (expires at - iat + 24hrs) │
     * └──────────────┴─────────────────────────────────────────┘
     *
     * WHY USE Jwts.builder()?
     *   → This is the JJWT library's fluent API for building tokens.
     *     It handles Base64 encoding, JSON serialization, and HMAC signing.
     *     You just provide the data and it does the rest.
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts
                .builder()
                .claims(extraClaims)                              // Add extra data first
                .subject(userDetails.getUsername())                // Set the "sub" claim
                .issuedAt(new Date(System.currentTimeMillis()))   // When the token was created
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))  // When it expires
                .signWith(getSigningKey())                        // Sign with our secret key
                .compact();                                       // Build the final token string
    }

    /*
     * ===== VALIDATE TOKEN =====
     *
     * Checks TWO things:
     * 1. Does the username in the token match the expected user?
     * 2. Has the token expired?
     *
     * Returns true ONLY if both checks pass.
     *
     * WHY CHECK THE USERNAME?
     *   → Prevents a valid token from being used for a different user.
     *     Even if someone has a valid token, it should only work for
     *     the user it was issued to.
     *
     * WHY CHECK EXPIRATION?
     *   → Even if the signature is valid, expired tokens should be rejected.
     *     The JJWT library actually throws an ExpiredJwtException
     *     during parsing, but we double-check here for safety.
     *
     * WHEN THIS IS CALLED:
     *   → JwtAuthenticationFilter calls this on every HTTP request
     *     that has a Bearer token in the Authorization header.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    // ================================================================
    // PRIVATE HELPER METHODS
    // ================================================================

    /*
     * ===== CHECK IF TOKEN IS EXPIRED =====
     *
     * Extracts the expiration date from the token and compares
     * it with the current time.
     *
     * token.exp = "2026-08-09T12:00:00"
     * now       = "2026-08-09T13:00:00"  → EXPIRED! (exp is before now)
     *
     * now       = "2026-08-09T11:00:00"  → VALID (exp is after now)
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /*
     * ===== EXTRACT EXPIRATION DATE =====
     *
     * Reads the "exp" claim from the token payload.
     * Returns a Date object representing when the token expires.
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /*
     * ===== GENERIC CLAIM EXTRACTOR =====
     *
     * This is a GENERIC method that extracts ANY claim from a token.
     * It uses Java's Function interface to accept a "claim resolver"
     * — a function that takes Claims and returns whatever you need.
     *
     * HOW IT WORKS:
     *   extractClaim(token, Claims::getSubject)     → Returns the username
     *   extractClaim(token, Claims::getExpiration)  → Returns expiration date
     *   extractClaim(token, c -> c.get("role"))     → Returns custom claim
     *
     * This avoids duplicating the "parse token → get claims" logic
     * in every extraction method. DRY principle!
     *
     * TYPE PARAMETER <T>:
     *   → The return type matches whatever the claimsResolver returns.
     *     If you extract a String claim, T = String.
     *     If you extract a Date claim, T = Date.
     */
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /*
     * ===== PARSE TOKEN AND EXTRACT ALL CLAIMS =====
     *
     * This is the CORE parsing method. It does three things:
     * 1. Takes the raw JWT string
     * 2. Verifies the signature using our secret key
     * 3. Parses the payload and returns all claims
     *
     * If the token is tampered with, the signature won't match
     * and JJWT throws a SignatureException.
     *
     * If the token is expired, JJWT throws ExpiredJwtException.
     *
     * If the token format is invalid, JJWT throws MalformedJwtException.
     *
     * All these exceptions bubble up and are caught by the
     * JwtAuthenticationFilter, which returns 401 Unauthorized.
     *
     * Jwts.parser()         → Creates a JWT parser
     *   .verifyWith(key)    → Sets the key to verify the signature
     *   .build()            → Builds the parser
     *   .parseSignedClaims  → Parses and verifies in one step
     *   .getPayload()       → Returns the claims (payload data)
     */
    private Claims extractAllClaims(String token) {
        return Jwts
                .parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /*
     * ===== GET THE SIGNING KEY =====
     *
     * Converts our Base64-encoded secret string into a SecretKey object
     * that JJWT can use for HMAC-SHA256 signing and verification.
     *
     * WHY Base64 DECODING?
     *   → The secret in application.properties is stored as a
     *     Base64-encoded string for safe storage. We decode it
     *     back to raw bytes before creating the key.
     *
     * WHY Keys.hmacShaKeyFor()?
     *   → This JJWT utility creates a proper SecretKey for HMAC.
     *     It also validates the key length — HMAC-SHA256 requires
     *     at least 256 bits (32 bytes). If your key is shorter,
     *     this method throws an exception at startup, preventing
     *     the use of a weak key.
     *
     * HMAC-SHA256 (HS256):
     *   → HMAC = Hash-based Message Authentication Code
     *     SHA-256 = The specific hash function used
     *     It's symmetric — the SAME key signs and verifies.
     *     This is simpler than RSA (asymmetric) and sufficient
     *     for single-server deployments.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

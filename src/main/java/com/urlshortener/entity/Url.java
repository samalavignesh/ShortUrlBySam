package com.urlshortener.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * =====================================================
 * URL ENTITY
 * =====================================================
 *
 * This is the CORE entity of our application.
 * It maps a short code (e.g., "abc123") to the original long URL.
 *
 * HOW URL SHORTENING WORKS:
 * ─────────────────────────
 * 1. User submits: "https://www.example.com/very/long/path?param=value"
 * 2. System generates a unique short code: "abc123"
 * 3. This entity stores the mapping: "abc123" → original URL
 * 4. When someone visits: http://localhost:8080/abc123
 *    → The system looks up "abc123" in this table
 *    → Redirects the browser to the original URL
 *    → Records a ClickEvent for analytics
 *
 * DATABASE TABLE GENERATED:
 * ┌────┬────────────┬───────────────────────────────────────┬─────────┬───────────┬──────────────┬──────────────┬──────────────┐
 * │ id │ short_code │ original_url                          │ user_id │ is_active │ expires_at   │ created_at   │ updated_at   │
 * ├────┼────────────┼───────────────────────────────────────┼─────────┼───────────┼──────────────┼──────────────┼──────────────┤
 * │ 1  │ abc123     │ https://www.example.com/long/path     │ 1       │ true      │ 2027-08-08   │ 2026-08-08   │ 2026-08-08   │
 * │ 2  │ xyz789     │ https://www.google.com/search?q=java  │ 1       │ true      │ null         │ 2026-08-08   │ 2026-08-08   │
 * └────┴────────────┴───────────────────────────────────────┴─────────┴───────────┴──────────────┴──────────────┴──────────────┘
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "urls")
public class Url implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * SHORT CODE — The unique identifier in the shortened URL.
     *
     * Example: In "http://localhost:8080/abc123", the short code is "abc123"
     *
     * nullable = false → Every URL must have a short code.
     * unique = true    → No two URLs can have the same short code.
     *                     This is CRITICAL — if two URLs shared "abc123",
     *                     the system wouldn't know which one to redirect to!
     * length = 10      → Short codes are kept short (6-10 chars typically).
     *                     Shorter = easier to share and remember.
     *
     * @Column also creates a DATABASE INDEX on this column automatically
     * because of unique = true. This makes lookups by short code very fast,
     * which is important because EVERY redirect queries this column.
     */
    @Column(nullable = false, unique = true, length = 10)
    private String shortCode;

    /*
     * ORIGINAL URL — The long URL that the short code points to.
     *
     * length = 2048 → Maximum URL length. Most browsers support URLs
     *                  up to ~2000 characters. 2048 gives us safe headroom.
     *
     * columnDefinition = "TEXT"
     *       → Overrides the default VARCHAR and uses PostgreSQL's TEXT type,
     *         which can store very long strings efficiently.
     *         We specify both length (for validation) and TEXT (for storage).
     *
     * Why not just use VARCHAR(2048)?
     *       → TEXT is more idiomatic in PostgreSQL for variable-length strings.
     *         There's no performance difference, but TEXT makes intent clearer.
     */
    @Column(nullable = false, length = 2048, columnDefinition = "TEXT")
    private String originalUrl;

    /*
     * ===== RELATIONSHIP: Many URLs → One User =====
     *
     * @ManyToOne → Defines the relationship:
     *              "Many URLs can belong to ONE User."
     *
     *   fetch = FetchType.LAZY
     *       → The User object is NOT loaded from DB when you fetch a URL.
     *         It's only loaded when you call url.getUser().
     *         This is important for redirect performance — when someone
     *         clicks a short URL, we only need the original URL,
     *         not the entire User object with all their data.
     *
     * @JoinColumn(name = "user_id", nullable = false)
     *       → This creates the FOREIGN KEY column "user_id" in the urls table.
     *         It references the "id" column of the "users" table.
     *
     *         nullable = false → Every URL MUST belong to a user.
     *                            Anonymous URL creation is not allowed.
     *
     *         This is the OWNING side of the relationship (it has the FK).
     *         The User entity's @OneToMany(mappedBy="user") is the inverse side.
     *
     * DATABASE PERSPECTIVE:
     *   urls table has a column: user_id BIGINT NOT NULL REFERENCES users(id)
     */
    /*
     * RELATIONSHIP: Many URLs → One User (LAZY loaded)
     *
     * @JsonIgnoreProperties → Breaks the infinite serialization loop.
     *
     * When Redis serializes a Url object, Jackson would normally follow
     * the User reference, then follow User.urls, then follow each Url's
     * User reference, and so on infinitely.
     *
     * By ignoring the "urls" and "password" fields on the User side
     * during serialization, we break the cycle:
     *   Url → User (only id, username, email, role) ← STOPS HERE ✅
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"urls", "password", "hibernateLazyInitializer", "handler"})
    private User user;

    /*
     * IS ACTIVE — Soft delete flag.
     *
     * Instead of physically deleting URLs from the database,
     * we set isActive = false. This is called a "SOFT DELETE".
     *
     * Why soft delete instead of hard delete?
     * ───────────────────────────────────────
     * 1. SAFETY: Accidentally deleted URLs can be recovered.
     * 2. ANALYTICS: We keep click history even for deactivated URLs.
     * 3. AUDIT TRAIL: We can see which URLs existed historically.
     * 4. SHORT CODE REUSE: We can prevent reusing short codes
     *    that were previously assigned.
     *
     * @Builder.Default → When using the Builder pattern,
     *   this field defaults to true (active) if not explicitly set.
     *   Without this, Builder would set it to false (Java's boolean default).
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = true;

    /*
     * EXPIRES AT — Optional expiration date for the short URL.
     *
     * nullable (default) → This field CAN be null, meaning the URL never expires.
     *
     * Use cases:
     * - Temporary marketing campaign links (expire after campaign ends)
     * - Time-limited sharing (e.g., "this link expires in 7 days")
     * - Security (sensitive links that should stop working after a period)
     *
     * If null → The URL lives forever (or until manually deactivated).
     * If set  → The redirect service checks this before redirecting.
     *           If expired, it returns a "URL has expired" error.
     */
    private LocalDateTime expiresAt;

    /*
     * CLICK COUNT — Denormalized counter for total clicks.
     *
     * "Denormalized" means we're storing a COMPUTED value that could
     * be calculated by counting ClickEvent records. Why duplicate it?
     *
     * PERFORMANCE REASON:
     * ───────────────────
     * - Counting clicks: SELECT COUNT(*) FROM click_events WHERE url_id = ?
     *   → This is SLOW for popular URLs with millions of clicks.
     *
     * - Reading a stored count: SELECT click_count FROM urls WHERE id = ?
     *   → This is INSTANT, regardless of how many clicks exist.
     *
     * We increment this counter atomically every time a click is recorded.
     * The detailed per-click data lives in the ClickEvent entity.
     *
     * @Builder.Default → Defaults to 0 when using Builder pattern.
     */
    @Column(nullable = false)
    @Builder.Default
    private Long clickCount = 0L;

    /* Auto-set timestamps — same concept as in User entity */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /*
     * ===== RELATIONSHIP: One URL → Many ClickEvents =====
     *
     * Same pattern as User → URLs relationship.
     * One URL can have many click events (every time someone visits it).
     *
     * cascade = CascadeType.ALL → Deleting a URL deletes all its click events.
     * orphanRemoval = true → Removing a click from the list deletes it from DB.
     * fetch = FetchType.LAZY → Click events are only loaded when requested.
     */
    @JsonIgnore  // Prevents LazyInitializationException when serializing to Redis cache
    @OneToMany(mappedBy = "url", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ClickEvent> clickEvents = new ArrayList<>();
}

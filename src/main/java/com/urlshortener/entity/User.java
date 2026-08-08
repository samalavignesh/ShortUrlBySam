package com.urlshortener.entity;

import com.urlshortener.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * =====================================================
 * USER ENTITY
 * =====================================================
 *
 * This class represents a registered user in our system.
 * JPA (Java Persistence API) maps this class to a database table.
 *
 * WHAT IS AN ENTITY?
 * ──────────────────
 * An Entity is a Java class that JPA maps directly to a database table.
 * Each instance of this class = one row in the "users" table.
 * Each field = one column in that table.
 *
 * WHY DO WE NEED A USER ENTITY?
 * ─────────────────────────────
 * - To store account credentials (username, email, password)
 * - To associate URLs with the user who created them
 * - To control access (authentication & authorization)
 * - To track when accounts were created/updated
 *
 * DATABASE TABLE GENERATED:
 * ┌────┬──────────┬─────────────────────┬──────────────┬──────┬────────────────┬────────────────┐
 * │ id │ username │ email               │ password     │ role │ created_at     │ updated_at     │
 * ├────┼──────────┼─────────────────────┼──────────────┼──────┼────────────────┼────────────────┤
 * │ 1  │ john     │ john@example.com    │ $2a$10$...   │ USER │ 2026-08-08 ... │ 2026-08-08 ... │
 * │ 2  │ admin    │ admin@example.com   │ $2a$10$...   │ADMIN │ 2026-08-08 ... │ 2026-08-08 ... │
 * └────┴──────────┴─────────────────────┴──────────────┴──────┴────────────────┴────────────────┘
 */

/*
 * ===== LOMBOK ANNOTATIONS (Code Generation) =====
 *
 * @Getter         → Auto-generates getter methods for ALL fields
 *                   (e.g., getUsername(), getEmail(), getRole())
 *
 * @Setter         → Auto-generates setter methods for ALL fields
 *                   (e.g., setUsername("john"), setEmail("john@email.com"))
 *
 * @NoArgsConstructor → Generates a no-argument constructor: new User()
 *                      JPA REQUIRES this — it creates objects via reflection.
 *
 * @AllArgsConstructor → Generates a constructor with ALL fields as parameters:
 *                       new User(1L, "john", "john@email.com", ...)
 *
 * @Builder        → Generates a Builder pattern so you can create objects like:
 *                   User.builder().username("john").email("john@email.com").build()
 *                   This is much cleaner than telescoping constructors.
 *
 * WITHOUT Lombok, you'd have to manually write ~50+ lines of
 * getters, setters, and constructors. Lombok generates them at compile time.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

/*
 * ===== JPA ANNOTATIONS (Database Mapping) =====
 *
 * @Entity  → Tells JPA: "This class represents a database table."
 *            Without this, JPA ignores the class completely.
 *
 * @Table(name = "users")
 *         → Specifies the actual table name in the database.
 *            Why "users" instead of "user"?
 *            Because "user" is a RESERVED KEYWORD in PostgreSQL!
 *            If we used "user", every SQL query would fail.
 */
@Entity
@Table(name = "users")
public class User {

    /*
     * @Id → Marks this field as the PRIMARY KEY of the table.
     *       Every JPA entity MUST have exactly one @Id field.
     *       This is how the database uniquely identifies each row.
     *
     * @GeneratedValue(strategy = GenerationType.IDENTITY)
     *       → Tells the database to AUTO-INCREMENT this value.
     *         PostgreSQL will use a SERIAL/BIGSERIAL column.
     *         You never set this manually — the DB assigns it.
     *
     *         Other strategies exist:
     *         - SEQUENCE: Uses a DB sequence (PostgreSQL preferred)
     *         - TABLE: Uses a separate table to track IDs
     *         - AUTO: Let JPA pick the strategy
     *         We use IDENTITY for simplicity.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * @Column(nullable = false, unique = true, length = 50)
     *       → Customizes how this field maps to a DB column:
     *
     *         nullable = false → Creates a NOT NULL constraint.
     *                            The database will REJECT any insert
     *                            that doesn't provide a username.
     *
     *         unique = true   → Creates a UNIQUE constraint.
     *                            No two users can have the same username.
     *                            The DB enforces this automatically.
     *
     *         length = 50     → Sets VARCHAR(50) in the database.
     *                            Limits username to 50 characters max.
     *                            This prevents abuse (storing huge strings).
     */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /*
     * Email field — unique per user for account recovery and identification.
     *
     * unique = true ensures no two accounts share the same email.
     * length = 100 allows for long email addresses.
     */
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /*
     * Password field — stores the HASHED password, never plain text!
     *
     * length = 255 because BCrypt hashes are ~60 chars, but we keep
     * headroom for other hashing algorithms that may produce longer hashes.
     *
     * SECURITY NOTE: We will use BCryptPasswordEncoder in the Security
     * layer to hash passwords before saving. The stored value looks like:
     * "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
     */
    @Column(nullable = false, length = 255)
    private String password;

    /*
     * @Enumerated(EnumType.STRING)
     *       → Tells JPA HOW to store the Role enum in the database.
     *
     *         EnumType.STRING → Stores the enum NAME as text: "USER", "ADMIN"
     *         EnumType.ORDINAL → Would store the position: 0, 1
     *
     *         We use STRING because:
     *         - It's human-readable in the database
     *         - If you reorder enum values, existing data doesn't break
     *         - Debugging is much easier (you see "ADMIN" not "1")
     *
     *         ORDINAL is dangerous: if you add a new role between
     *         existing ones, all stored values shift and become wrong!
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /*
     * @CreationTimestamp
     *       → Hibernate annotation that AUTOMATICALLY sets this field
     *         to the current date/time when the entity is first saved.
     *         You never need to set this manually.
     *
     * updatable = false → Prevents this field from being changed
     *                      after the initial insert. Once set, it's permanent.
     *
     * LocalDateTime → Java's modern date/time class (replaces old java.util.Date).
     *                  Maps to TIMESTAMP in PostgreSQL.
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /*
     * @UpdateTimestamp
     *       → Hibernate annotation that AUTOMATICALLY updates this field
     *         to the current date/time every time the entity is modified.
     *         Useful for tracking "last modified" timestamps.
     */
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /*
     * ===== RELATIONSHIP: One User → Many URLs =====
     *
     * @OneToMany → Defines a one-to-many relationship:
     *              "One User can own MANY URLs."
     *
     *   mappedBy = "user"
     *       → This is the INVERSE side of the relationship.
     *         The "user" field in the Url entity OWNS the relationship
     *         (it has the foreign key column in its table).
     *         This side just reads the relationship, doesn't create a column.
     *
     *   cascade = CascadeType.ALL
     *       → Operations on User cascade to their URLs:
     *         - If you SAVE a User with new URLs → URLs are saved too
     *         - If you DELETE a User → All their URLs are deleted too
     *         Think of it as "parent controls child lifecycle."
     *
     *   orphanRemoval = true
     *       → If you remove a URL from this list, it gets DELETED from DB.
     *         Without this, removing from the list just sets the FK to null.
     *
     *   fetch = FetchType.LAZY
     *       → URLs are NOT loaded from DB until you actually call getUrls().
     *         This is a PERFORMANCE optimization — if you only need the
     *         user's name, you don't want to load 1000 URLs unnecessarily.
     *         EAGER would load ALL URLs every time you fetch a User.
     *
     * Why ArrayList?
     *       → We initialize the list to avoid NullPointerException.
     *         Without this, urls would be null until Hibernate loads it.
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Url> urls = new ArrayList<>();
}

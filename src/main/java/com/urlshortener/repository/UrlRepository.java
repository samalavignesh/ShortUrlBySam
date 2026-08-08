package com.urlshortener.repository;

import com.urlshortener.entity.Url;
import com.urlshortener.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * =====================================================
 * URL REPOSITORY
 * =====================================================
 *
 * This is the most important repository in the application.
 * It handles all database operations for shortened URLs.
 *
 * This repository uses TWO types of query methods:
 *
 * 1. DERIVED QUERIES (method name → SQL)
 *    Spring reads the method name and generates the query.
 *    Example: findByShortCode → SELECT * FROM urls WHERE short_code = ?
 *
 * 2. CUSTOM @Query (JPQL written manually)
 *    For complex queries that can't be expressed via method names.
 *    JPQL = Java Persistence Query Language (like SQL but uses entity names).
 */
@Repository
public interface UrlRepository extends JpaRepository<Url, Long> {

    /*
     * ===== CORE QUERY: Find URL by Short Code =====
     *
     * This is the MOST CALLED method in the entire application!
     * Every time someone visits a short URL, this method runs.
     *
     * Flow: User visits http://localhost:8080/abc123
     *       → Controller extracts "abc123"
     *       → Calls findByShortCode("abc123")
     *       → Gets the Url entity with the original URL
     *       → Redirects the user to the original URL
     *
     * Generated SQL: SELECT * FROM urls WHERE short_code = ?
     *
     * PERFORMANCE: The "short_code" column has a UNIQUE index
     * (from @Column(unique=true) in the entity), so this lookup
     * is O(1) — instant even with millions of URLs in the database.
     *
     * Returns Optional because the short code might not exist
     * (typo, deleted URL, etc.).
     */
    Optional<Url> findByShortCode(String shortCode);

    /*
     * ===== CHECK: Does a Short Code Already Exist? =====
     *
     * Called during URL creation to ensure the randomly generated
     * short code doesn't collide with an existing one.
     *
     * Flow:
     *   1. Generate random code "abc123"
     *   2. Check: existsByShortCode("abc123") → true (collision!)
     *   3. Generate another code "xyz789"
     *   4. Check: existsByShortCode("xyz789") → false (safe to use!)
     *   5. Save the URL with code "xyz789"
     *
     * Generated SQL: SELECT EXISTS(SELECT 1 FROM urls WHERE short_code = ?)
     *
     * WHY NOT JUST TRY TO INSERT AND CATCH THE EXCEPTION?
     * Because random collisions are expected (especially as the database
     * grows), and using exceptions for expected flow control is bad practice.
     */
    boolean existsByShortCode(String shortCode);

    /*
     * ===== USER'S URLs: Get All URLs for a Specific User =====
     *
     * Method name breakdown:
     *   find   → SELECT
     *   By     → WHERE
     *   User   → The "user" field in the Url entity (the @ManyToOne relation)
     *
     * Generated SQL: SELECT * FROM urls WHERE user_id = ?
     *
     * Spring is smart enough to know that "User" refers to the
     * @ManyToOne relationship, and it uses the foreign key (user_id)
     * in the WHERE clause.
     *
     * Returns a List because a user can have many URLs.
     * The list will be empty (not null) if the user has no URLs.
     *
     * USAGE: Dashboard page showing "Your Shortened URLs"
     */
    List<Url> findByUser(User user);

    /*
     * ===== ACTIVE URLs ONLY: Filter by User AND Active Status =====
     *
     * Compound query — filters on TWO conditions.
     *
     * Method name breakdown:
     *   find      → SELECT
     *   By        → WHERE
     *   User      → user_id = ?
     *   And       → AND (combines conditions)
     *   IsActive  → is_active = ?
     *
     * Generated SQL: SELECT * FROM urls WHERE user_id = ? AND is_active = ?
     *
     * USAGE:
     *   findByUserAndIsActive(currentUser, true)  → User's active URLs
     *   findByUserAndIsActive(currentUser, false) → User's deactivated URLs
     *
     * WHY THIS METHOD?
     * When showing a user their URLs, we typically want to show only
     * active ones. Deactivated URLs can be shown in a separate "trash" view.
     */
    List<Url> findByUserAndIsActive(User user, boolean isActive);

    /*
     * ===== CUSTOM JPQL: Increment Click Count Atomically =====
     *
     * THIS IS THE MOST IMPORTANT QUERY FOR DATA INTEGRITY!
     *
     * WHY NOT JUST DO: url.setClickCount(url.getClickCount() + 1)?
     * ──────────────────────────────────────────────────────────
     * That approach has a RACE CONDITION:
     *
     *   Thread 1: reads clickCount = 100
     *   Thread 2: reads clickCount = 100
     *   Thread 1: writes clickCount = 101
     *   Thread 2: writes clickCount = 101  ← WRONG! Should be 102!
     *
     * This is called the "lost update" problem.
     *
     * THE FIX — Atomic Database Update:
     *   UPDATE urls SET click_count = click_count + 1 WHERE id = ?
     *
     * The database handles the increment atomically, meaning:
     *   Thread 1: UPDATE → click_count becomes 101
     *   Thread 2: UPDATE → click_count becomes 102  ← CORRECT!
     *
     * Both threads can run simultaneously without conflicts because
     * the database uses row-level locking for the UPDATE operation.
     *
     * ANNOTATIONS EXPLAINED:
     *
     * @Modifying
     *   → Tells Spring this query CHANGES data (it's not a SELECT).
     *     Without this, Spring assumes all @Query methods are read-only
     *     and throws an exception when you try to UPDATE or DELETE.
     *
     * @Query("...")
     *   → A custom JPQL query. JPQL looks like SQL but uses
     *     ENTITY NAMES (Url) instead of TABLE NAMES (urls),
     *     and FIELD NAMES (clickCount) instead of COLUMN NAMES (click_count).
     *
     *     JPQL: UPDATE Url u SET u.clickCount = u.clickCount + 1 WHERE u.id = :id
     *     SQL:  UPDATE urls SET click_count = click_count + 1 WHERE id = ?
     *
     * @Param("id")
     *   → Binds the method parameter to the :id placeholder in the query.
     *     This prevents SQL injection — the value is parameterized,
     *     not concatenated into the query string.
     */
    @Modifying
    @Query("UPDATE Url u SET u.clickCount = u.clickCount + 1 WHERE u.id = :id")
    void incrementClickCount(@Param("id") Long id);
}

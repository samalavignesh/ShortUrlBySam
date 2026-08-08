package com.urlshortener.repository;

import com.urlshortener.entity.ClickEvent;
import com.urlshortener.entity.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * =====================================================
 * CLICK EVENT REPOSITORY
 * =====================================================
 *
 * Handles all database operations for click analytics data.
 *
 * This repository demonstrates ADVANCED Spring Data query derivation —
 * building complex queries with multiple conditions, date ranges,
 * ordering, and counting, all from method names alone.
 *
 * QUERY DERIVATION CHEAT SHEET:
 * ┌─────────────────────┬────────────────────────────────────────┐
 * │ Keyword in Name     │ SQL Equivalent                        │
 * ├─────────────────────┼────────────────────────────────────────┤
 * │ findBy              │ SELECT * FROM ... WHERE ...            │
 * │ countBy             │ SELECT COUNT(*) FROM ... WHERE ...     │
 * │ And                 │ AND                                    │
 * │ Between             │ BETWEEN ? AND ?                        │
 * │ OrderBy...Desc      │ ORDER BY ... DESC                     │
 * │ GreaterThanEqual    │ >= ?                                   │
 * │ LessThanEqual       │ <= ?                                   │
 * └─────────────────────┴────────────────────────────────────────┘
 */
@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    /*
     * ===== ALL CLICKS FOR A URL =====
     *
     * Returns every click event for a given URL, ordered by most recent first.
     *
     * Method name breakdown:
     *   find         → SELECT
     *   By           → WHERE
     *   Url          → url_id = ? (the @ManyToOne FK)
     *   OrderBy      → ORDER BY
     *   ClickedAt    → clicked_at column
     *   Desc         → DESC (newest first)
     *
     * Generated SQL:
     *   SELECT * FROM click_events
     *   WHERE url_id = ?
     *   ORDER BY clicked_at DESC
     *
     * USAGE: "Show me all clicks on this URL, newest first"
     * This feeds the detailed analytics view for a specific URL.
     */
    List<ClickEvent> findByUrlOrderByClickedAtDesc(Url url);

    /*
     * ===== TOTAL CLICK COUNT FOR A URL =====
     *
     * Counts the number of click events for a specific URL.
     *
     * Method name breakdown:
     *   count  → SELECT COUNT(*)
     *   By     → WHERE
     *   Url    → url_id = ?
     *
     * Generated SQL: SELECT COUNT(*) FROM click_events WHERE url_id = ?
     *
     * WHY DO WE HAVE THIS WHEN Url ALREADY HAS clickCount?
     * ──────────────────────────────────────────────────────
     * The denormalized clickCount on Url is for FAST reads.
     * This method is for VERIFICATION — if you suspect the count
     * is out of sync (due to bugs or data migration), you can
     * compare: url.getClickCount() vs clickEventRepository.countByUrl(url)
     *
     * It's also useful for generating analytics that the
     * denormalized count doesn't cover (like filtered counts).
     */
    long countByUrl(Url url);

    /*
     * ===== CLICKS IN A DATE RANGE =====
     *
     * Returns clicks that happened between two timestamps.
     *
     * Method name breakdown:
     *   find           → SELECT
     *   By             → WHERE
     *   Url            → url_id = ?
     *   And            → AND
     *   ClickedAt      → clicked_at
     *   Between        → BETWEEN ? AND ?
     *
     * Generated SQL:
     *   SELECT * FROM click_events
     *   WHERE url_id = ? AND clicked_at BETWEEN ? AND ?
     *
     * USAGE EXAMPLES:
     *   - "Clicks in the last 24 hours":
     *     findByUrlAndClickedAtBetween(url, now.minusDays(1), now)
     *
     *   - "Clicks this week":
     *     findByUrlAndClickedAtBetween(url, startOfWeek, endOfWeek)
     *
     *   - "Clicks in August 2026":
     *     findByUrlAndClickedAtBetween(url, aug1, aug31)
     *
     * This powers the time-filtered analytics dashboard
     * (e.g., "Show me clicks from last 7 days").
     */
    List<ClickEvent> findByUrlAndClickedAtBetween(
            Url url,
            LocalDateTime start,
            LocalDateTime end
    );

    /*
     * ===== COUNT CLICKS IN A DATE RANGE =====
     *
     * Same as above but returns just the COUNT instead of all records.
     *
     * Generated SQL:
     *   SELECT COUNT(*) FROM click_events
     *   WHERE url_id = ? AND clicked_at BETWEEN ? AND ?
     *
     * WHY BOTH findBy AND countBy VERSIONS?
     * ─────────────────────────────────────
     * - countBy → When you just need the NUMBER (fast, low memory)
     *   "How many clicks happened yesterday?" → 1,523
     *
     * - findBy → When you need the ACTUAL RECORDS (for detailed analysis)
     *   "Show me every click from yesterday with IP and browser info"
     *
     * For a chart showing "clicks per day for the last 30 days",
     * you'd call countBy... 30 times — once per day.
     * That's much faster than loading ALL click records for 30 days.
     */
    long countByUrlAndClickedAtBetween(
            Url url,
            LocalDateTime start,
            LocalDateTime end
    );

    /*
     * ===== ALL CLICKS FOR A URL SINCE A GIVEN TIME =====
     *
     * Method name breakdown:
     *   find              → SELECT
     *   By                → WHERE
     *   Url               → url_id = ?
     *   And               → AND
     *   ClickedAt         → clicked_at
     *   GreaterThanEqual  → >= ?
     *
     * Generated SQL:
     *   SELECT * FROM click_events
     *   WHERE url_id = ? AND clicked_at >= ?
     *
     * USAGE: "Show all clicks since last Monday"
     *   findByUrlAndClickedAtGreaterThanEqual(url, lastMonday)
     *
     * Simpler than 'Between' when you don't need an end bound.
     */
    List<ClickEvent> findByUrlAndClickedAtGreaterThanEqual(
            Url url,
            LocalDateTime since
    );
}

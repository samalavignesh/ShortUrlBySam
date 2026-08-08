package com.urlshortener.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * =====================================================
 * CLICK EVENT ENTITY
 * =====================================================
 *
 * Records every single click/visit on a shortened URL.
 * This is the foundation of our ANALYTICS feature.
 *
 * WHY TRACK CLICKS?
 * ─────────────────
 * URL shorteners aren't just about making links shorter.
 * The real value is ANALYTICS — understanding WHO clicks your links,
 * WHEN they click, WHERE they come from, and WHAT device they use.
 *
 * WHAT WE CAPTURE PER CLICK:
 * ──────────────────────────
 * - WHEN: Timestamp of the click
 * - WHO: IP address (for geographic analysis)
 * - WHAT: User-Agent string (browser, OS, device type)
 * - WHERE FROM: Referer header (which website sent the visitor)
 *
 * This data enables dashboards like:
 * - "Your link got 1,234 clicks today"
 * - "65% of clicks came from mobile devices"
 * - "Most clicks came from Twitter"
 * - "Peak hours are 2-4 PM"
 *
 * DATABASE TABLE GENERATED:
 * ┌────┬────────┬─────────────────┬────────────────┬──────────────────────────────┬──────────────────────┐
 * │ id │ url_id │ ip_address      │ clicked_at     │ user_agent                   │ referer              │
 * ├────┼────────┼─────────────────┼────────────────┼──────────────────────────────┼──────────────────────┤
 * │ 1  │ 1      │ 192.168.1.100   │ 2026-08-08 ... │ Mozilla/5.0 (Windows NT ...) │ https://twitter.com  │
 * │ 2  │ 1      │ 10.0.0.50       │ 2026-08-08 ... │ Mozilla/5.0 (iPhone; ...)    │ null                 │
 * │ 3  │ 2      │ 172.16.0.1      │ 2026-08-08 ... │ Mozilla/5.0 (Linux; ...)     │ https://reddit.com   │
 * └────┴────────┴─────────────────┴────────────────┴──────────────────────────────┴──────────────────────┘
 *
 * PERFORMANCE NOTE:
 * ─────────────────
 * This table can grow VERY large for popular URLs (millions of rows).
 * That's why we also store a denormalized clickCount on the Url entity,
 * so simple "total clicks" queries don't need to scan this whole table.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "click_events")
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * ===== RELATIONSHIP: Many ClickEvents → One URL =====
     *
     * @ManyToOne → Many click events belong to one URL.
     *
     *   fetch = FetchType.LAZY
     *       → When we load a ClickEvent, we DON'T automatically load
     *         the entire Url object. We only load it if we call getUrl().
     *         This is important for batch analytics processing where
     *         we might process thousands of click events.
     *
     * @JoinColumn(name = "url_id", nullable = false)
     *       → Creates the foreign key column "url_id" pointing to urls.id.
     *         Every click MUST be associated with a URL.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "url_id", nullable = false)
    private Url url;

    /*
     * CLICKED AT — When the click happened.
     *
     * @CreationTimestamp → Automatically set when the record is inserted.
     *   We never update click events — they're append-only (immutable).
     *   Once a click is recorded, the data never changes.
     *
     * updatable = false → Enforces immutability at the JPA level.
     *
     * Why LocalDateTime?
     *   → Java's modern date-time class. Maps to TIMESTAMP in PostgreSQL.
     *     Includes both date and time (e.g., "2026-08-08T14:30:00").
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime clickedAt;

    /*
     * IP ADDRESS — The visitor's IP address.
     *
     * length = 45 → Accommodates both:
     *   - IPv4: "192.168.1.100" (max 15 chars)
     *   - IPv6: "2001:0db8:85a3:0000:0000:8a2e:0370:7334" (max 39 chars)
     *   - IPv4-mapped IPv6: "::ffff:192.168.1.100" (max 45 chars)
     *
     * USE CASES:
     *   - Geographic analytics (IP → Country/City via GeoIP services)
     *   - Rate limiting (prevent click fraud from one IP)
     *   - Security auditing
     *
     * PRIVACY NOTE: In production, you might want to anonymize IPs
     * (e.g., mask the last octet: "192.168.1.xxx") to comply with
     * GDPR and other privacy regulations.
     */
    @Column(length = 45)
    private String ipAddress;

    /*
     * USER AGENT — The browser/device identification string.
     *
     * Example values:
     *   "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
     *   "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X)"
     *   "Mozilla/5.0 (Linux; Android 13; Pixel 7)"
     *
     * length = 500 → User-Agent strings can be quite long,
     *                 especially with extension lists.
     *
     * USE CASES:
     *   - Device breakdown (Desktop vs Mobile vs Tablet)
     *   - Browser analytics (Chrome vs Safari vs Firefox)
     *   - OS analytics (Windows vs macOS vs Android vs iOS)
     *
     * We parse this string in the analytics service to extract
     * meaningful device/browser information.
     */
    @Column(length = 500)
    private String userAgent;

    /*
     * REFERER — The website/page the visitor came FROM.
     *
     * Example: If someone clicks your short URL from a tweet,
     * the referer would be "https://twitter.com/..."
     *
     * length = 2048 → Referer URLs can be quite long.
     *
     * NOTE: Can be null! The referer header is:
     *   - Not sent when typing the URL directly in the browser
     *   - Not sent when clicking from HTTPS → HTTP (security downgrade)
     *   - Not sent when the referring page has rel="noreferrer"
     *   - Blocked by some browsers/extensions for privacy
     *
     * USE CASES:
     *   - Traffic source analysis ("Where are clicks coming from?")
     *   - Campaign tracking ("Which social media drives the most traffic?")
     */
    @Column(length = 2048)
    private String referer;
}

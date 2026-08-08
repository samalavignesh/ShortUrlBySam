package com.urlshortener.repository;

import com.urlshortener.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * =====================================================
 * USER REPOSITORY
 * =====================================================
 *
 * WHAT IS A REPOSITORY?
 * ─────────────────────
 * A Repository is the layer between your application and the database.
 * It provides methods to Create, Read, Update, and Delete (CRUD) entities
 * WITHOUT you writing any SQL queries.
 *
 * HOW DOES THIS WORK WITH ZERO IMPLEMENTATION?
 * ─────────────────────────────────────────────
 * This is an INTERFACE, not a class. There's no method body anywhere!
 * Yet it fully works. Here's the magic:
 *
 * 1. We extend JpaRepository<User, Long>
 *    - "User" = the entity type this repository manages
 *    - "Long" = the type of the entity's primary key (@Id field)
 *
 * 2. At application startup, Spring Data JPA automatically creates
 *    a CONCRETE CLASS that implements this interface behind the scenes.
 *    This is called a "proxy" — Spring generates the implementation for you.
 *
 * 3. JpaRepository gives us these methods FOR FREE (inherited):
 *    ┌──────────────────────────────────────┬───────────────────────────────────────┐
 *    │ Method                               │ SQL Equivalent                        │
 *    ├──────────────────────────────────────┼───────────────────────────────────────┤
 *    │ save(user)                           │ INSERT INTO users (...) VALUES (...)   │
 *    │ findById(1L)                         │ SELECT * FROM users WHERE id = 1      │
 *    │ findAll()                            │ SELECT * FROM users                   │
 *    │ deleteById(1L)                       │ DELETE FROM users WHERE id = 1        │
 *    │ count()                              │ SELECT COUNT(*) FROM users            │
 *    │ existsById(1L)                       │ SELECT EXISTS(... WHERE id = 1)       │
 *    │ saveAll(List<User>)                  │ Batch INSERT                          │
 *    │ findAll(Sort.by("username"))         │ SELECT * FROM users ORDER BY username │
 *    │ findAll(PageRequest.of(0, 10))       │ SELECT * FROM users LIMIT 10 OFFSET 0│
 *    └──────────────────────────────────────┴───────────────────────────────────────┘
 *
 * WHAT IS @Repository?
 * ────────────────────
 * @Repository is a Spring annotation that:
 * 1. Marks this interface as a Spring-managed component (bean)
 * 2. Enables automatic exception translation — raw database exceptions
 *    (like SQLIntegrityConstraintViolationException) get converted to
 *    Spring's cleaner DataAccessException hierarchy.
 *
 * NOTE: For Spring Data JPA, @Repository is technically optional
 * (Spring auto-detects interfaces extending JpaRepository), but
 * we include it for clarity and best practice.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /*
     * ===== CUSTOM QUERY: Find User by Username =====
     *
     * SPRING DATA QUERY DERIVATION — THE NAMING CONVENTION MAGIC
     * ───────────────────────────────────────────────────────────
     * Spring Data JPA can generate SQL queries just from the METHOD NAME!
     * It parses the method name and builds the query automatically.
     *
     * Method name breakdown:
     *   find  → SELECT (we want to retrieve data)
     *   By    → WHERE (filter condition follows)
     *   Username → The field name in the User entity
     *
     * Spring generates: SELECT * FROM users WHERE username = ?
     *
     * WHY Optional<User> INSTEAD OF User?
     * ────────────────────────────────────
     * Optional<User> means the result MIGHT be empty (user not found).
     *
     * Without Optional:
     *   User user = userRepository.findByUsername("john");
     *   // user could be null! If you forget to check → NullPointerException!
     *
     * With Optional:
     *   Optional<User> user = userRepository.findByUsername("john");
     *   user.ifPresent(u -> System.out.println(u.getEmail()));
     *   // Or: user.orElseThrow(() -> new UserNotFoundException("john"));
     *
     * Optional FORCES you to handle the "not found" case explicitly.
     * This prevents bugs and makes the code self-documenting.
     *
     * USAGE IN SERVICE LAYER:
     *   User user = userRepository.findByUsername("john")
     *       .orElseThrow(() -> new ResourceNotFoundException("User not found"));
     */
    Optional<User> findByUsername(String username);

    /*
     * ===== CUSTOM QUERY: Find User by Email =====
     *
     * Same pattern as findByUsername, but searches by email.
     * Used for: login by email, password reset, account recovery.
     *
     * Generated SQL: SELECT * FROM users WHERE email = ?
     */
    Optional<User> findByEmail(String email);

    /*
     * ===== EXISTENCE CHECK: Does a Username Already Exist? =====
     *
     * Method name breakdown:
     *   exists → SELECT EXISTS (returns true/false)
     *   By     → WHERE
     *   Username → The field to check
     *
     * Generated SQL: SELECT EXISTS(SELECT 1 FROM users WHERE username = ?)
     *
     * WHY THIS METHOD?
     * ────────────────
     * During user REGISTRATION, we need to check if a username
     * is already taken BEFORE trying to insert.
     *
     * Alternative: We could just call save() and catch the
     * unique constraint violation. But that's:
     * 1. Ugly — using exceptions for flow control is an anti-pattern
     * 2. Slow — the DB has to attempt the insert before failing
     * 3. Hard to differentiate — "was it username or email that was duplicate?"
     *
     * existsBy is cleaner, faster, and gives a clear boolean answer.
     *
     * Returns: true if a user with this username exists, false otherwise.
     */
    boolean existsByUsername(String username);

    /*
     * ===== EXISTENCE CHECK: Does an Email Already Exist? =====
     *
     * Same pattern as existsByUsername.
     * Used during registration to check for duplicate emails.
     *
     * Generated SQL: SELECT EXISTS(SELECT 1 FROM users WHERE email = ?)
     */
    boolean existsByEmail(String email);
}

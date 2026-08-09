package com.urlshortener.auth.service;

import com.urlshortener.entity.User;
import com.urlshortener.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * =====================================================
 * CUSTOM USER DETAILS SERVICE
 * =====================================================
 *
 * This service bridges OUR User entity with Spring Security's
 * authentication system. It's the answer to the question:
 * "How does Spring Security know about OUR users?"
 *
 * WHAT IS UserDetailsService?
 * ───────────────────────────
 * UserDetailsService is a Spring Security INTERFACE with one method:
 *   UserDetails loadUserByUsername(String username)
 *
 * Spring Security calls this method during authentication to look up
 * a user's details (password hash, roles, etc.) from your data source.
 *
 * THE AUTHENTICATION FLOW (Where this fits):
 * ───────────────────────────────────────────
 *
 * ┌─────────┐    POST /api/auth/login    ┌──────────────────────┐
 * │ Client  │ ─────────────────────────→ │ AuthenticationManager │
 * │         │  {"username":"john",       │                      │
 * │         │   "password":"secret"}     │  "I need to verify   │
 * └─────────┘                            │   this user..."      │
 *                                        └──────────┬───────────┘
 *                                                   │
 *                                     calls loadUserByUsername("john")
 *                                                   │
 *                                                   ▼
 *                                   ┌───────────────────────────────┐
 *                                   │   CustomUserDetailsService    │
 *                                   │                               │
 *                                   │  1. Query DB for "john"       │
 *                                   │  2. Found? Build UserDetails  │
 *                                   │  3. Not found? Throw exception│
 *                                   └───────────────┬───────────────┘
 *                                                   │
 *                                                   ▼
 *                                   ┌───────────────────────────────┐
 *                                   │   AuthenticationManager       │
 *                                   │                               │
 *                                   │  Compares submitted password  │
 *                                   │  with UserDetails.password    │
 *                                   │  using BCryptPasswordEncoder  │
 *                                   │                               │
 *                                   │  Match? → Authentication OK   │
 *                                   │  No match? → 401 Unauthorized │
 *                                   └───────────────────────────────┘
 *
 * WHY CAN'T SPRING SECURITY USE OUR User ENTITY DIRECTLY?
 * ────────────────────────────────────────────────────────
 * Spring Security doesn't know about our User class. It works with
 * its OWN interface: UserDetails. So we need to CONVERT our User
 * entity into a UserDetails object. That's what this service does.
 *
 * Think of it as a TRANSLATOR:
 *   Our User entity  →  [CustomUserDetailsService]  →  Spring's UserDetails
 *
 * @RequiredArgsConstructor (Lombok)
 *   → Generates a constructor for all 'final' fields.
 *     Spring uses this constructor to inject the UserRepository.
 *     This is called "constructor injection" — the recommended
 *     way to inject dependencies in Spring.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    /*
     * The UserRepository is injected by Spring via constructor injection
     * (thanks to @RequiredArgsConstructor on final fields).
     *
     * We use this to look up users from the database by username.
     */
    private final UserRepository userRepository;

    /*
     * ===== LOAD USER BY USERNAME =====
     *
     * This is the ONE method required by UserDetailsService.
     * Spring Security calls it automatically when authenticating a user.
     *
     * WHAT THIS METHOD DOES:
     * 1. Receives a username (from the login request)
     * 2. Queries the database for a user with that username
     * 3. If found → Converts our User to Spring's UserDetails format
     * 4. If not found → Throws UsernameNotFoundException
     *
     * IMPORTANT: Spring Security catches UsernameNotFoundException
     * and converts it to BadCredentialsException (which our
     * GlobalExceptionHandler returns as 401).
     * This prevents USERNAME ENUMERATION — the client gets the same
     * "Invalid username or password" error regardless of whether
     * the username exists or not.
     *
     * @param username  The username to look up (from login form)
     * @return UserDetails  Spring Security's user representation
     * @throws UsernameNotFoundException  If the user doesn't exist
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        /*
         * Step 1: Query the database for the user.
         *
         * userRepository.findByUsername() returns Optional<User>.
         * If the user doesn't exist, .orElseThrow() fires and we
         * throw UsernameNotFoundException.
         */
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with username: " + username
                ));

        /*
         * Step 2: Convert our User entity to Spring Security's UserDetails.
         *
         * We use Spring's built-in User builder (not our entity) to create
         * a UserDetails object. This is the cleanest approach.
         *
         * WHAT EACH FIELD MEANS:
         *
         * .username(user.getUsername())
         *   → The principal identifier. Spring Security uses this to
         *     identify the currently authenticated user.
         *
         * .password(user.getPassword())
         *   → The HASHED password from our database.
         *     AuthenticationManager will compare the submitted password
         *     (after hashing) with this stored hash.
         *
         * .authorities(...)
         *   → The user's ROLES/PERMISSIONS. These determine what
         *     endpoints the user can access.
         *
         *     We prefix with "ROLE_" because Spring Security's
         *     hasRole("USER") internally checks for "ROLE_USER".
         *     This is a Spring Security CONVENTION:
         *
         *     ┌────────────────────────┬───────────────────────────┐
         *     │  You write             │  Spring checks for        │
         *     ├────────────────────────┼───────────────────────────┤
         *     │  hasRole("USER")       │  "ROLE_USER" authority    │
         *     │  hasRole("ADMIN")      │  "ROLE_ADMIN" authority   │
         *     │  hasAuthority("X")     │  "X" authority (no prefix)│
         *     └────────────────────────┴───────────────────────────┘
         *
         *     So if our Role enum is USER, the authority becomes "ROLE_USER".
         *
         * SimpleGrantedAuthority:
         *   → Spring Security's simplest implementation of GrantedAuthority.
         *     It's just a wrapper around a String (the role name).
         *     List.of() creates an immutable single-element list.
         */
        return org.springframework.security.core.userdetails.User
                .builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(List.of(
                        new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
                ))
                .build();
    }
}


package com.urlshortener.auth.service;
import com.urlshortener.dto.request.ForgotPasswordRequest;
import com.urlshortener.dto.request.ResetPasswordRequest;
import org.springframework.beans.factory.annotation.Autowired;
import com.urlshortener.dto.request.LoginRequest;
import com.urlshortener.dto.request.RegisterRequest;
import com.urlshortener.dto.response.AuthResponse;
import com.urlshortener.entity.User;
import com.urlshortener.enums.Role;
import com.urlshortener.exception.DuplicateResourceException;
import com.urlshortener.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import java.security.SecureRandom;
/**
 * =====================================================
 * AUTH SERVICE
 * =====================================================
 *
 * This is the BUSINESS LOGIC layer for authentication.
 * It handles two core operations: REGISTRATION and LOGIN.
 *
 * WHERE DOES THIS FIT IN THE ARCHITECTURE?
 * ─────────────────────────────────────────
 *
 *  ┌─────────┐     ┌────────────────┐     ┌─────────────┐     ┌────────────────┐
 *  │ Client  │ ──→ │ AuthController │ ──→ │ AuthService  │ ──→ │ UserRepository │
 *  │         │     │  (REST layer)  │     │ (this class) │     │   (database)   │
 *  └─────────┘     └────────────────┘     └──────┬───────┘     └────────────────┘
 *                                                │
 *                                     ┌──────────┼──────────┐
 *                                     ▼          ▼          ▼
 *                              PasswordEncoder JwtService  AuthManager
 *                              (hash passwords) (tokens)  (verify creds)
 *
 * RESPONSIBILITIES:
 * ─────────────────
 * 1. REGISTER: Validate uniqueness → Hash password → Save user → Generate JWT
 * 2. LOGIN: Verify credentials → Generate JWT → Return token
 *
 * This class does NOT handle:
 * - HTTP request/response (that's the Controller's job)
 * - Database queries directly (that's the Repository's job)
 * - JWT token mechanics (that's the JwtService's job)
 * - Password hashing details (that's the PasswordEncoder's job)
 *
 * This separation is called SINGLE RESPONSIBILITY PRINCIPLE (SRP):
 * Each class does ONE thing well.
 *
 * @Service → Marks this as a Spring-managed service bean.
 *            Spring creates one instance and injects it wherever needed.
 *            @Service is semantically identical to @Component, but
 *            communicates "this class contains business logic."
 *
 * @RequiredArgsConstructor → Lombok generates a constructor with all
 *                            'final' fields. Spring uses this for
 *                            dependency injection (constructor injection).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /*
     * ===== DEPENDENCIES (injected by Spring via constructor) =====
     *
     * All fields are 'final' — they're set once via the constructor
     * and never changed. This is the recommended pattern because:
     * 1. IMMUTABILITY: Dependencies can't be accidentally reassigned
     * 2. REQUIRED: The class can't be created without all dependencies
     * 3. TESTABILITY: Easy to mock in unit tests via constructor
     */

    /*
     * UserRepository — handles all database operations for the User entity.
     * We use it to:
     *   - Check if a username/email already exists (during registration)
     *   - Save new users to the database
     *   - Find users by username (during login, via AuthManager → UserDetailsService)
     */
    private final UserRepository userRepository;
    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private StringRedisTemplate redisTemplate;
    /*
     * PasswordEncoder — hashes passwords using BCrypt.
     * We use it during REGISTRATION to hash the raw password before saving.
     * During LOGIN, the AuthenticationManager uses it internally
     * to compare the submitted password with the stored hash.
     *
     * We NEVER store raw passwords. The flow is:
     *   "myPassword123" → BCrypt → "$2a$10$N9qo8u..."  (stored in DB)
     */
    private final PasswordEncoder passwordEncoder;

    /*
     * JwtService — generates and validates JWT tokens.
     * After successful registration or login, we call
     * jwtService.generateToken() to create a JWT for the user.
     */
    private final JwtService jwtService;

    /*
     * AuthenticationManager — Spring Security's authentication entry point.
     * During LOGIN, we call authenticationManager.authenticate() which:
     * 1. Calls our CustomUserDetailsService.loadUserByUsername()
     * 2. Compares the submitted password with the stored hash
     * 3. Throws BadCredentialsException if credentials are wrong
     *
     * We don't do password comparison manually — we delegate to Spring Security.
     * This is the recommended approach because the AuthenticationManager:
     * - Handles all edge cases (locked accounts, disabled users, etc.)
     * - Publishes security events (for audit logging)
     * - Integrates with Spring Security's ecosystem
     */
    private final AuthenticationManager authenticationManager;

    // ================================================================
    // PUBLIC METHODS
    // ================================================================

    /*
     * ===== REGISTER A NEW USER =====
     *
     * This method handles the FULL user registration flow.
     *
     * FLOW:
     * ┌──────────────────────────────────────────────────────────────────┐
     * │  1. CHECK if username already exists → DuplicateResourceException│
     * │  2. CHECK if email already exists    → DuplicateResourceException│
     * │  3. HASH the raw password with BCrypt                           │
     * │  4. BUILD a User entity with the hashed password                │
     * │  5. SAVE the user to the database                               │
     * │  6. GENERATE a JWT token for immediate login                    │
     * │  7. RETURN AuthResponse (token + username + role)               │
     * └──────────────────────────────────────────────────────────────────┘
     *
     * WHY CHECK UNIQUENESS BEFORE SAVING?
     * ────────────────────────────────────
     * We could skip the checks and let the database throw a
     * DataIntegrityViolationException when the UNIQUE constraint is violated.
     * But that approach has problems:
     *
     * 1. UNCLEAR ERRORS: The DB exception doesn't clearly say
     *    whether it was the USERNAME or EMAIL that was duplicate.
     *    Our pre-check gives a precise, user-friendly error.
     *
     * 2. WASTED WORK: Without pre-checking, we'd hash the password
     *    (an expensive BCrypt operation) before discovering the duplicate.
     *    Pre-checking avoids this waste.
     *
     * 3. CLEAN FLOW: Using exceptions for expected scenarios
     *    (like "username already taken") is an anti-pattern.
     *    Pre-checking keeps the happy path clean.
     *
     * WHY GENERATE A TOKEN IMMEDIATELY?
     * ──────────────────────────────────
     * After registration, the user is logged in automatically.
     * This avoids the annoying UX of "registered successfully, now log in."
     * The client gets a token right away and can start making authenticated requests.
     *
     * @param request The RegisterRequest DTO with username, email, password
     * @return AuthResponse containing the JWT token, username, and role
     * @throws DuplicateResourceException if username or email already exists
     */
	public void sendPasswordResetOtp(String email) {
        if (!userRepository.existsByEmail(email)) {
            throw new RuntimeException("No account found with this email address");
        }

        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        redisTemplate.opsForValue().set("RESET_OTP:" + email, otp, Duration.ofMinutes(5));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Password Reset Code - URL Shortener");
        message.setText("Hello,\n\nYour password reset code is: " + otp + "\n\nThis code will expire in 5 minutes.\nIf you did not request this, please ignore this email.");
        mailSender.send(message);
    }

    public void resetPassword(ResetPasswordRequest request) {
        String storedOtp = redisTemplate.opsForValue().get("RESET_OTP:" + request.getEmail());
        if (storedOtp == null || !storedOtp.equals(request.getOtp())) {
            throw new RuntimeException("Invalid or expired reset code");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        redisTemplate.delete("RESET_OTP:" + request.getEmail());
    }
    public AuthResponse register(RegisterRequest request) {

        /*
         * STEP 1: Check if the username is already taken.
         *
         * userRepository.existsByUsername() runs:
         *   SELECT EXISTS(SELECT 1 FROM users WHERE username = ?)
         *
         * This is a fast boolean check — no need to load the full user object.
         * If true, we throw DuplicateResourceException which the
         * GlobalExceptionHandler converts to HTTP 409 Conflict.
         */
	String storedOtp = redisTemplate.opsForValue().get("REG_OTP:" + request.getEmail());
		if (storedOtp == null || !storedOtp.equals(request.getOtp())) {
    		throw new RuntimeException("Invalid or expired verification code");
	}
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("User", "username", request.getUsername());
        }

        /*
         * STEP 2: Check if the email is already registered.
         *
         * Same pattern as username check. We check both because:
         * - A user might try a unique username but reuse an email
         * - Both have UNIQUE constraints in the database
         * - We want to tell the user WHICH field is the problem
         */
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        /*
         * STEP 3 & 4: Build the User entity.
         *
         * We use the Builder pattern (generated by Lombok's @Builder)
         * to create the User object. This is much cleaner than a
         * constructor with 6+ parameters.
         *
         * KEY POINTS:
         *
         * passwordEncoder.encode(request.getPassword())
         *   → Takes the raw password "myPassword123"
         *   → Generates a random salt
         *   → Hashes it with BCrypt: "$2a$10$N9qo8u..."
         *   → This is what gets stored in the database
         *   → The raw password is NEVER stored or logged
         *
         * Role.USER
         *   → New users always get the USER role.
         *   → The ADMIN role is assigned manually (via DB or admin endpoint).
         *   → We NEVER let the client specify their own role!
         *     If we accepted role from the request, anyone could register
         *     as ADMIN and take over the system.
         *
         * Fields we DON'T set:
         *   - id → Auto-generated by PostgreSQL (IDENTITY strategy)
         *   - createdAt → Auto-set by @CreationTimestamp
         *   - updatedAt → Auto-set by @UpdateTimestamp
         *   - urls → Defaults to empty ArrayList (@Builder.Default)
         */
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

        /*
         * STEP 5: Save the user to the database.
         *
         * userRepository.save(user) does:
         *   INSERT INTO users (username, email, password, role, created_at, updated_at)
         *   VALUES ('john_doe', 'john@example.com', '$2a$10$...', 'USER', NOW(), NOW())
         *
         * The returned 'savedUser' has the auto-generated ID populated.
         * We use savedUser (not user) from this point on because it has
         * all DB-generated values (id, timestamps).
         */
        User savedUser = userRepository.save(user);
	redisTemplate.delete("REG_OTP:" + request.getEmail());
        /*
         * STEP 6: Generate a JWT token for the newly registered user.
         *
         * We create a Spring Security UserDetails object from our User entity.
         * This is needed because JwtService.generateToken() expects UserDetails
         * (Spring Security's interface) not our custom User entity.
         *
         * The token payload will contain:
         *   - sub: "john_doe" (from username)
         *   - iat: current timestamp (issued at)
         *   - exp: current + 24 hours (expiration)
         */
        String jwtToken = jwtService.generateToken(
                org.springframework.security.core.userdetails.User
                        .builder()
                        .username(savedUser.getUsername())
                        .password(savedUser.getPassword())
                        .authorities("ROLE_" + savedUser.getRole().name())
                        .build()
        );

        /*
         * STEP 7: Build and return the AuthResponse.
         *
         * The response includes:
         *   - token: The JWT string for authentication
         *   - username: For the UI to display "Welcome, john_doe!"
         *   - role: For the UI to show/hide admin features
         */
        return AuthResponse.builder()
                .token(jwtToken)
                .username(savedUser.getUsername())
                .role(savedUser.getRole().name())
                .build();
    }

    /*
     * ===== LOGIN AN EXISTING USER =====
     *
     * This method handles user authentication (login).
     *
     * FLOW:
     * ┌──────────────────────────────────────────────────────────────────┐
     * │  1. AUTHENTICATE credentials via Spring Security's              │
     * │     AuthenticationManager                                       │
     * │  2. LOAD the user from the database                             │
     * │  3. GENERATE a JWT token                                        │
     * │  4. RETURN AuthResponse (token + username + role)               │
     * └──────────────────────────────────────────────────────────────────┘
     *
     * WHAT HAPPENS INSIDE authenticationManager.authenticate()?
     * ──────────────────────────────────────────────────────────
     *
     * ┌────────────────────────────────────────────────────────────────┐
     * │  authenticationManager.authenticate(credentials)              │
     * │       │                                                       │
     * │       ▼                                                       │
     * │  DaoAuthenticationProvider                                    │
     * │       │                                                       │
     * │       ├──→ CustomUserDetailsService.loadUserByUsername("john") │
     * │       │       │                                               │
     * │       │       └──→ UserRepository.findByUsername("john")       │
     * │       │              │                                        │
     * │       │              └──→ Returns User from DB                │
     * │       │                                                       │
     * │       ├──→ BCryptPasswordEncoder.matches(rawPwd, hashedPwd)   │
     * │       │       │                                               │
     * │       │       ├──→ Match?     → Return Authentication ✅     │
     * │       │       └──→ No match?  → Throw BadCredentialsException │
     * │       │                                                       │
     * │       └──→ User not found? → Throw UsernameNotFoundException  │
     * │               (converted to BadCredentialsException)          │
     * └────────────────────────────────────────────────────────────────┘
     *
     * IMPORTANT SECURITY DETAIL:
     * ──────────────────────────
     * Both "user not found" and "wrong password" result in the SAME
     * BadCredentialsException → "Invalid username or password".
     *
     * This prevents USERNAME ENUMERATION attacks:
     * - Without this: "User not found" vs "Wrong password"
     *   → Attacker learns which usernames EXIST in the system
     * - With this: Always "Invalid username or password"
     *   → Attacker can't determine if the username exists
     *
     * The GlobalExceptionHandler catches BadCredentialsException
     * and returns HTTP 401 with a generic error message.
     *
     * @param request The LoginRequest DTO with username and password
     * @return AuthResponse containing the JWT token, username, and role
     * @throws BadCredentialsException (via AuthManager) if credentials are invalid
     */
    public void sendRegistrationOtp(String email) {
    if (userRepository.existsByEmail(email)) {
        throw new RuntimeException("Email is already registered");
    }

    // Generate random 6-digit OTP
    String otp = String.format("%06d", SECURE_RANDOM.nextInt(1000000));

    // Store in Redis with a 5-minute TTL
    redisTemplate.opsForValue().set("REG_OTP:" + email, otp, Duration.ofMinutes(5));

    // Send Email
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(email);
    message.setSubject("Your Verification Code - URL Shortener");
    message.setText("Welcome!\n\nYour verification code is: " + otp + "\n\nThis code will expire in 5 minutes.");
    mailSender.send(message);
}
    public AuthResponse login(LoginRequest request) {

        /*
         * STEP 1: Authenticate the credentials.
         *
         * UsernamePasswordAuthenticationToken wraps the username + password
         * into Spring Security's authentication request format.
         *
         * authenticationManager.authenticate() triggers the ENTIRE
         * authentication flow described above. If authentication fails,
         * it throws BadCredentialsException — we DON'T catch it here.
         * We let it bubble up to the GlobalExceptionHandler.
         *
         * If this line completes without throwing → credentials are VALID.
         */
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        /*
         * STEP 2: Load the full user from the database.
         *
         * We've already verified the credentials, so we KNOW the user exists.
         * We load the user to get their role and username for the response.
         *
         * WHY LOAD AGAIN? Didn't the AuthenticationManager already load it?
         * ─────────────────────────────────────────────────────────────────
         * Yes, the AuthenticationManager loaded a UserDetails object via
         * CustomUserDetailsService. But UserDetails is Spring Security's
         * interface — it doesn't have our custom fields (like Role enum).
         *
         * We could extract the user from the Authentication result, but
         * it's simpler and more readable to load our own User entity.
         * The cost is one extra SELECT query, which is negligible.
         *
         * .orElseThrow() should NEVER trigger here because we just
         * authenticated successfully. But defensive coding is good practice.
         */
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(); // Should never happen after successful auth

        /*
         * STEP 3: Generate a JWT token.
         *
         * Same process as in register() — create a UserDetails object
         * and pass it to JwtService to generate the token.
         */
        String jwtToken = jwtService.generateToken(
                org.springframework.security.core.userdetails.User
                        .builder()
                        .username(user.getUsername())
                        .password(user.getPassword())
                        .authorities("ROLE_" + user.getRole().name())
                        .build()
        );

        /*
         * STEP 4: Build and return the AuthResponse.
         */
        return AuthResponse.builder()
                .token(jwtToken)
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }

    public void logout(String jwt) {
        java.util.Date expiration = jwtService.extractExpiration(jwt);
        long ttlMillis = expiration.getTime() - System.currentTimeMillis();
        if (ttlMillis > 0) {
            redisTemplate.opsForValue().set("BL_JWT:" + jwt, "logout", java.time.Duration.ofMillis(ttlMillis));
        }
    }
}

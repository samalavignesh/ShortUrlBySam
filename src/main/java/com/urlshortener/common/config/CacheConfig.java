package com.urlshortener.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * =====================================================
 * REDIS CACHE CONFIGURATION
 * =====================================================
 *
 * Configures Redis as the caching layer for the URL Shortener.
 *
 * WHY CACHING?
 * ────────────
 * The redirect endpoint (/{shortCode} → original URL) is the HOTTEST
 * path in a URL shortener. Every time someone clicks a short link,
 * we need to look up the original URL. Without caching, every single
 * click hits the database.
 *
 * With Redis caching:
 * ┌────────┐    Cache HIT     ┌───────┐
 * │ Client │ ───────────────→ │ Redis │ → Return immediately (~1ms)
 * └────────┘                  └───────┘
 *                             Cache MISS
 *                                ↓
 *                          ┌──────────┐
 *                          │ Database │ → Fetch + store in Redis
 *                          └──────────┘
 *
 * PERFORMANCE IMPACT:
 * ───────────────────
 * Without cache: Every redirect → DB query (~5-20ms)
 * With Redis:    Popular URLs served from memory (~0.1-1ms)
 * That's a 10-100x speedup for hot URLs!
 *
 * WHY REDIS (vs simple in-memory cache)?
 * ───────────────────────────────────────
 * 1. DISTRIBUTED: Shared across multiple app instances (horizontal scaling)
 * 2. PERSISTENT: Survives app restarts (unlike ConcurrentHashMap)
 * 3. TTL SUPPORT: Automatic expiration of stale entries
 * 4. SCALABLE: Can handle millions of cached entries
 * 5. ATOMIC OPS: Built-in support for counters, rate limiting, etc.
 *
 * CACHE NAMES:
 * ────────────
 * "urlCache"       → Caches URL lookups by shortCode (for redirects)
 *                    TTL: 10 minutes
 * "urlListCache"   → Caches user's URL lists (for dashboard)
 *                    TTL: 5 minutes
 *
 * PREREQUISITES:
 * ──────────────
 * Redis server must be running on localhost:6379 (default)
 * Install: winget install Redis.Redis
 * Start:   redis-server (or runs as a Windows service)
 *
 * @EnableCaching → Activates Spring's caching infrastructure.
 *                  This enables @Cacheable, @CacheEvict, @CachePut
 *                  annotations throughout the application.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * ===== JACKSON OBJECT MAPPER FOR REDIS =====
     *
     * A dedicated ObjectMapper configured to handle Java 8 date/time types.
     *
     * WHY DO WE NEED THIS?
     * ─────────────────────
     * By default, Jackson does NOT know how to serialize java.time.LocalDateTime.
     * Without this, caching any DTO that has a LocalDateTime field (like
     * UrlResponse.createdAt or ClickEventResponse.clickedAt) will throw:
     *   SerializationException: Java 8 date/time type not supported by default
     *
     * FIX:
     * ─────
     * 1. Register JavaTimeModule → teaches Jackson how to read/write java.time.*
     * 2. Disable WRITE_DATES_AS_TIMESTAMPS → stores dates as ISO-8601 strings
     *    e.g. "2026-08-15T17:34:26" instead of [2026, 8, 15, 17, 34, 26]
     *    Strings are human-readable in redis-cli and work across Java versions.
     */
    
    private ObjectMapper cacheObjectMapper() {
	ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Prevent failures when Hibernate lazy proxy beans have no accessible properties
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        // Ignore unknown properties during deserialization (forward compatibility)
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // Enable default typing so Jackson stores class names in JSON,
        // allowing correct deserialization back to concrete types (e.g., CachedUrl, List)
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);

        return mapper;
    }

    /**
     * Creates a RedisCacheManager that manages all application caches.
     *
     * Each cache has its own TTL (Time To Live):
     * - urlCache:     10 minutes — short URLs don't change often,
     *                 but we want to detect deactivation reasonably fast
     * - urlListCache: 5 minutes  — user's URL list changes more frequently
     *                 (when they create/delete URLs)
     *
     * KEY SERIALIZATION:
     * → Keys are stored as Strings (human-readable in Redis CLI)
     *   Example key: "urlCache::abc123"
     *
     * VALUE SERIALIZATION:
     * → Values are stored as JSON (using Jackson)
     *   This allows us to inspect cached values in Redis CLI
     *   and ensures cross-version compatibility.
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        /* Use our custom mapper that supports LocalDateTime */
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(cacheObjectMapper());

        /* Default cache configuration: 10-minute TTL, JSON serialization */
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        /* Per-cache TTL overrides */
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        /* URL cache: 10-minute TTL for redirect lookups */
        cacheConfigurations.put("urlCache", defaultConfig.entryTtl(Duration.ofMinutes(10)));

        /* URL list cache: 5-minute TTL for user dashboards */
        cacheConfigurations.put("urlListCache", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * ===== REDIS TEMPLATE (for manual Redis operations) =====
     *
     * While @Cacheable handles most caching automatically,
     * sometimes we need direct Redis access:
     * - Storing rate-limit counters
     * - Pub/sub for real-time analytics
     * - Custom data structures (sorted sets for leaderboards)
     *
     * This template uses:
     * - StringRedisSerializer for keys → human-readable
     * - GenericJackson2JsonRedisSerializer for values → JSON format
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {

        /* Use our custom mapper that supports LocalDateTime */
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(cacheObjectMapper());

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        /* Keys stored as plain strings */
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        /* Values stored as JSON with LocalDateTime support */
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}

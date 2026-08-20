package com.urlshortener.analytics.messaging;

import com.urlshortener.analytics.service.AnalyticsParsingService;
import com.urlshortener.entity.ClickEvent;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.dto.event.ClickEventPayload;
import com.urlshortener.entity.Url;
import com.urlshortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.urlshortener.common.config.RedisStreamConfig.CONSUMER_GROUP;
import static com.urlshortener.common.config.RedisStreamConfig.STREAM_KEY;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClickEventConsumer implements StreamListener<String, ObjectRecord<String, String>> {

    private final UrlRepository urlRepository;
    private final ClickEventRepository clickEventRepository;
    private final AnalyticsParsingService parsingService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final org.springframework.cache.CacheManager cacheManager;

    @Override
    @Transactional
    public void onMessage(ObjectRecord<String, String> message) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String value = message.getValue();
            if (value != null && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1).replace("\\\"", "\"");
            }
            ClickEventPayload payload = mapper.readValue(value, ClickEventPayload.class);

            // 1. Increment click count atomically
            urlRepository.incrementClickCount(payload.getUrlId());

            // 2. Save the ClickEvent
            Url urlRef = urlRepository.getReferenceById(payload.getUrlId());
            ClickEvent clickEvent = ClickEvent.builder()
                    .url(urlRef)
                    .ipAddress(payload.getIpAddress())
                    .userAgent(payload.getUserAgent())
                    .referer(payload.getReferer())
                    .build();

            // Parse User-Agent and GeoIP
            parsingService.parseEvent(clickEvent);

            clickEventRepository.save(clickEvent);

            log.debug("Processed click event for URL ID: {}", payload.getUrlId());

            // Acknowledge the message so it is removed from the pending entries list
            redisTemplate.opsForStream().acknowledge(STREAM_KEY, CONSUMER_GROUP, message.getId());

            // Evict the user's dashboard cache so the click count updates instantly
            String username = urlRef.getUser().getUsername();
            org.springframework.cache.Cache cache = cacheManager.getCache("urlListCache");
            if (cache != null) {
                cache.evict(username);
            }

        } catch (Exception e) {
            log.error("Failed to process click event from Redis Stream: {}", message.getId(), e);
        }
    }
}

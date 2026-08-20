package com.urlshortener.analytics.messaging;

import com.urlshortener.dto.event.ClickEventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import static com.urlshortener.common.config.RedisStreamConfig.STREAM_KEY;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClickEventProducer {

    private final RedisTemplate<String, Object> redisTemplate;

    public void publishEvent(ClickEventPayload payload) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(payload);
            ObjectRecord<String, String> record = StreamRecords.newRecord()
                    .ofObject(json)
                    .withStreamKey(STREAM_KEY);
            redisTemplate.opsForStream().add(record);
            log.debug("Published click event for URL ID: {}", payload.getUrlId());
        } catch (Exception e) {
            log.error("Failed to serialize and publish event", e);
        }
    }
}

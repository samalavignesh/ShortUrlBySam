package com.urlshortener.common.config;

import com.urlshortener.analytics.messaging.ClickEventConsumer;
import com.urlshortener.dto.event.ClickEventPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.data.redis.serializer.RedisSerializer;

import jakarta.annotation.PostConstruct;
import java.time.Duration;

@Configuration
public class RedisStreamConfig {

    public static final String STREAM_KEY = "click-events-stream";
    public static final String CONSUMER_GROUP = "analytics-group";

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ClickEventConsumer clickEventConsumer;

    @PostConstruct
    public void createConsumerGroupIfNotExists() {
        try {
            redisTemplate.opsForStream().createGroup(STREAM_KEY, CONSUMER_GROUP);
        } catch (Exception e) {
            // Consumer group likely already exists, which is fine
        }
    }

    @Bean
    public Subscription clickEventSubscription(StreamMessageListenerContainer<String, ObjectRecord<String, String>> listenerContainer) {
        Subscription subscription = listenerContainer.receive(
                Consumer.from(CONSUMER_GROUP, "analytics-consumer-1"),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                clickEventConsumer
        );
        listenerContainer.start();
        return subscription;
    }

    @Bean
    public StreamMessageListenerContainer<String, ObjectRecord<String, String>> streamMessageListenerContainer() {
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, ObjectRecord<String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .targetType(String.class)
                        .build();

        return StreamMessageListenerContainer.create(redisConnectionFactory, options);
    }
}

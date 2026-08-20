package com.urlshortener.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Payload sent to Redis Streams when a URL is clicked.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickEventPayload implements Serializable {
    private Long urlId;
    private String ipAddress;
    private String userAgent;
    private String referer;
}

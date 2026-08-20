package com.urlshortener.analytics.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import com.urlshortener.entity.ClickEvent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import ua_parser.Client;
import ua_parser.Parser;

import java.io.InputStream;
import java.net.InetAddress;

@Slf4j
@Service
public class AnalyticsParsingService {

    private Parser uaParser;
    private DatabaseReader geoDbReader;

    @PostConstruct
    public void init() {
        try {
            log.info("Initializing User-Agent Parser...");
            uaParser = new Parser();
            log.info("User-Agent Parser initialized successfully.");

            log.info("Initializing GeoIP Database...");
            ClassPathResource resource = new ClassPathResource("GeoLite2-City.mmdb");
            if (resource.exists()) {
                InputStream is = resource.getInputStream();
                geoDbReader = new DatabaseReader.Builder(is).build();
                log.info("GeoIP Database initialized successfully.");
            } else {
                log.warn("GeoLite2-City.mmdb not found. GeoIP parsing will be disabled.");
            }
        } catch (Exception e) {
            log.error("Failed to initialize Analytics Parsing Service: {}", e.getMessage(), e);
        }
    }

    public void parseEvent(ClickEvent clickEvent) {
        parseUserAgent(clickEvent);
        parseGeoIp(clickEvent);
    }

    private void parseUserAgent(ClickEvent clickEvent) {
        if (clickEvent.getUserAgent() == null || uaParser == null) {
            return;
        }

        try {
            Client c = uaParser.parse(clickEvent.getUserAgent());
            
            if (c.os != null && c.os.family != null) {
                clickEvent.setOs(c.os.family);
            }
            if (c.userAgent != null && c.userAgent.family != null) {
                clickEvent.setBrowser(c.userAgent.family);
            }
            if (c.device != null && c.device.family != null) {
                clickEvent.setDeviceType(c.device.family);
            }
        } catch (Exception e) {
            log.debug("Failed to parse User-Agent {}: {}", clickEvent.getUserAgent(), e.getMessage());
        }
    }

    private void parseGeoIp(ClickEvent clickEvent) {
        if (clickEvent.getIpAddress() == null || geoDbReader == null) {
            return;
        }

        // Localhost ips don't resolve
        if (clickEvent.getIpAddress().equals("127.0.0.1") || clickEvent.getIpAddress().equals("0:0:0:0:0:0:0:1")) {
            clickEvent.setCountry("Localhost");
            clickEvent.setCity("Localhost");
            return;
        }

        try {
            InetAddress ipAddress = InetAddress.getByName(clickEvent.getIpAddress());
            CityResponse response = geoDbReader.city(ipAddress);

            if (response != null) {
                if (response.getCountry() != null && response.getCountry().getName() != null) {
                    clickEvent.setCountry(response.getCountry().getName());
                }
                if (response.getCity() != null && response.getCity().getName() != null) {
                    clickEvent.setCity(response.getCity().getName());
                }
            }
        } catch (Exception e) {
            // AddressNotFoundException is common for private/unmapped IPs, keep it debug
            log.debug("Failed to parse GeoIP for {}: {}", clickEvent.getIpAddress(), e.getMessage());
        }
    }
}

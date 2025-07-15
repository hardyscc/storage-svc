package com.storagesvc.security;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class S3CredentialService {

    private final Map<String, String> credentials = new ConcurrentHashMap<>();

    // Default to 15 minutes (900 seconds) to match AWS default
    @Value("${s3.timestamp.tolerance.seconds:900}")
    private long timestampToleranceSeconds;

    @Value("${app.access-key}")
    private String appAccessKey;

    @Value("${app.secret-key}")
    private String appSecretKey;

    @PostConstruct
    public void initializeCredentials() {
        // Initialize with credentials from configuration
        credentials.put(appAccessKey, appSecretKey);
    }

    public String getSecretKey(String accessKey) {
        return credentials.get(accessKey);
    }

    public boolean userExists(String accessKey) {
        return credentials.containsKey(accessKey);
    }

    public void addUser(String accessKey, String secretKey) {
        credentials.put(accessKey, secretKey);
    }

    public void removeUser(String accessKey) {
        credentials.remove(accessKey);
    }

    /**
     * Validates that the request timestamp is within the acceptable time window
     * to prevent replay attacks.
     * 
     * @param timestamp The timestamp from X-Amz-Date header (ISO 8601 format)
     * @return true if timestamp is valid, false otherwise
     */
    public boolean isTimestampValid(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return false;
        }

        try {
            Instant requestTime;

            // Handle both AWS timestamp formats
            if (timestamp.contains("-") || timestamp.length() > 16) {
                // ISO 8601 format: 2023-01-01T12:00:00Z or 2023-01-01T12:00:00.000Z
                requestTime = Instant.parse(timestamp);
            } else {
                // AWS basic format: 20230101T120000Z
                requestTime = Instant.from(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                        .withZone(java.time.ZoneOffset.UTC)
                        .parse(timestamp, Instant::from));
            }

            Instant currentTime = Instant.now();
            long timeDifferenceSeconds = Math.abs(currentTime.getEpochSecond() - requestTime.getEpochSecond());

            return timeDifferenceSeconds <= timestampToleranceSeconds;

        } catch (DateTimeParseException e) {
            // Invalid timestamp format
            return false;
        }
    }
}

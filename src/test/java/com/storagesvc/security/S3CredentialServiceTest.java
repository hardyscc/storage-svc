package com.storagesvc.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class S3CredentialServiceTest {

    private S3CredentialService credentialService;

    @BeforeEach
    void setUp() {
        credentialService = new S3CredentialService();
        // Set timestamp tolerance to 900 seconds (15 minutes)
        ReflectionTestUtils.setField(credentialService, "timestampToleranceSeconds", 900L);
    }

    @Test
    void testValidCurrentTimestamp() {
        String currentTimestamp = Instant.now().toString();
        assertTrue(credentialService.isTimestampValid(currentTimestamp));
    }

    @Test
    void testValidRecentTimestamp() {
        // Timestamp from 5 minutes ago should be valid
        String recentTimestamp = Instant.now().minusSeconds(300).toString();
        assertTrue(credentialService.isTimestampValid(recentTimestamp));
    }

    @Test
    void testInvalidOldTimestamp() {
        // Timestamp from 20 minutes ago should be invalid (exceeds 15 minute tolerance)
        String oldTimestamp = Instant.now().minusSeconds(1200).toString();
        assertFalse(credentialService.isTimestampValid(oldTimestamp));
    }

    @Test
    void testInvalidFutureTimestamp() {
        // Timestamp from 20 minutes in the future should be invalid
        String futureTimestamp = Instant.now().plusSeconds(1200).toString();
        assertFalse(credentialService.isTimestampValid(futureTimestamp));
    }

    @Test
    void testValidBasicFormat() {
        // Test AWS basic timestamp format: 20230101T120000Z
        String basicFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .format(Instant.now().atZone(java.time.ZoneOffset.UTC));
        assertTrue(credentialService.isTimestampValid(basicFormat));
    }

    @Test
    void testInvalidTimestampFormat() {
        assertFalse(credentialService.isTimestampValid("invalid-timestamp"));
    }

    @Test
    void testNullTimestamp() {
        assertFalse(credentialService.isTimestampValid(null));
    }

    @Test
    void testEmptyTimestamp() {
        assertFalse(credentialService.isTimestampValid(""));
    }

    @Test
    void testUserManagement() {
        // Test existing functionality still works
        assertTrue(credentialService.userExists("minioadmin"));
        assertEquals("minioadmin", credentialService.getSecretKey("minioadmin"));

        credentialService.addUser("newuser", "newsecret");
        assertTrue(credentialService.userExists("newuser"));
        assertEquals("newsecret", credentialService.getSecretKey("newuser"));

        credentialService.removeUser("newuser");
        assertFalse(credentialService.userExists("newuser"));
    }
}

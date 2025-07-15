package com.storagesvc.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AwsSignatureValidator {

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String TERMINATOR = "aws4_request";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SHA256 = "SHA-256";

    public boolean validateSignature(String accessKey, String secretKey, String signature,
            String dateStamp, String region, String service,
            String signedHeaders, HttpServletRequest request) {
        try {
            // Calculate the expected signature
            String expectedSignature = calculateSignature(accessKey, secretKey, dateStamp,
                    region, service, signedHeaders, request);

            // Compare signatures (constant time comparison to prevent timing attacks)
            return constantTimeEquals(signature, expectedSignature);
        } catch (Exception e) {
            return false;
        }
    }

    private String calculateSignature(String accessKey, String secretKey, String dateStamp,
            String region, String service, String signedHeaders,
            HttpServletRequest request) throws Exception {

        // Step 1: Create canonical request
        String canonicalRequest = createCanonicalRequest(request, signedHeaders);

        // Step 2: Create string to sign
        String stringToSign = createStringToSign(canonicalRequest, dateStamp, region, service, request);

        // Step 3: Calculate signature
        byte[] signingKey = getSignatureKey(secretKey, dateStamp, region, service);
        return bytesToHex(hmacSha256(stringToSign.getBytes(StandardCharsets.UTF_8), signingKey));
    }

    private String createCanonicalRequest(HttpServletRequest request, String signedHeaders)
            throws NoSuchAlgorithmException {
        StringBuilder canonicalRequest = new StringBuilder();

        // HTTP method
        canonicalRequest.append(request.getMethod()).append("\n");

        // Canonical URI
        String canonicalUri = request.getRequestURI();
        if (canonicalUri == null || canonicalUri.isEmpty()) {
            canonicalUri = "/";
        }
        // URL encode the path but keep the forward slashes
        canonicalRequest.append(canonicalUri).append("\n");

        // Canonical query string
        String queryString = request.getQueryString();
        canonicalRequest.append(canonicalizeQueryString(queryString)).append("\n");

        // Canonical headers
        Map<String, String> headers = getCanonicalHeaders(request, signedHeaders);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            canonicalRequest.append(entry.getKey()).append(":").append(entry.getValue()).append("\n");
        }
        canonicalRequest.append("\n");

        // Signed headers
        canonicalRequest.append(signedHeaders).append("\n");

        // Payload hash
        String payloadHash = getPayloadHash(request);
        canonicalRequest.append(payloadHash);

        return canonicalRequest.toString();
    }

    private String canonicalizeQueryString(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return "";
        }

        Map<String, String> params = new TreeMap<>();
        String[] pairs = queryString.split("&");

        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            String key = keyValue[0];
            String value = keyValue.length > 1 ? keyValue[1] : "";
            params.put(key, value);
        }

        return params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    private Map<String, String> getCanonicalHeaders(HttpServletRequest request, String signedHeaders) {
        Map<String, String> headers = new TreeMap<>();
        List<String> headerNames = Arrays.asList(signedHeaders.split(";"));

        for (String headerName : headerNames) {
            String headerValue = request.getHeader(headerName);
            if (headerValue != null) {
                // Normalize header value (trim whitespace, collapse multiple spaces)
                headerValue = headerValue.trim().replaceAll("\\s+", " ");
                headers.put(headerName.toLowerCase(), headerValue);
            }
        }

        return headers;
    }

    private String getPayloadHash(HttpServletRequest request) {
        // Check if there's an x-amz-content-sha256 header
        String contentSha256 = request.getHeader("x-amz-content-sha256");
        if (contentSha256 != null) {
            return contentSha256;
        }

        // For streaming or large requests, AWS often uses UNSIGNED-PAYLOAD
        // For simplicity, we'll accept this for now
        return "UNSIGNED-PAYLOAD";
    }

    private String createStringToSign(String canonicalRequest, String dateStamp,
            String region, String service, HttpServletRequest request) throws NoSuchAlgorithmException {
        StringBuilder stringToSign = new StringBuilder();

        // Algorithm
        stringToSign.append(ALGORITHM).append("\n");

        // Request date/time
        String timestamp = request.getHeader("x-amz-date");
        if (timestamp == null) {
            timestamp = request.getHeader("date");
        }
        stringToSign.append(timestamp).append("\n");

        // Credential scope
        String credentialScope = dateStamp + "/" + region + "/" + service + "/" + TERMINATOR;
        stringToSign.append(credentialScope).append("\n");

        // Hashed canonical request
        String hashedCanonicalRequest = sha256Hash(canonicalRequest);
        stringToSign.append(hashedCanonicalRequest);

        return stringToSign.toString();
    }

    private byte[] getSignatureKey(String secretKey, String dateStamp, String region, String service) throws Exception {
        byte[] kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(dateStamp.getBytes(StandardCharsets.UTF_8), kSecret);
        byte[] kRegion = hmacSha256(region.getBytes(StandardCharsets.UTF_8), kDate);
        byte[] kService = hmacSha256(service.getBytes(StandardCharsets.UTF_8), kRegion);
        byte[] kSigning = hmacSha256(TERMINATOR.getBytes(StandardCharsets.UTF_8), kService);
        return kSigning;
    }

    private byte[] hmacSha256(byte[] data, byte[] key) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(key, HMAC_SHA256));
        return mac.doFinal(data);
    }

    private String sha256Hash(String data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(SHA256);
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}

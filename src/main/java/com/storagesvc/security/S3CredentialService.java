package com.storagesvc.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class S3CredentialService {

    private final Map<String, String> credentials = new ConcurrentHashMap<>();

    public S3CredentialService() {
        // Initialize with default credentials (same as MinIO default)
        credentials.put("minioadmin", "minioadmin");
        // You can add more users here
        credentials.put("testuser", "testpassword");
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
}

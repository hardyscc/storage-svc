package com.storagesvc.security;

import java.util.Collection;
import java.util.Collections;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import jakarta.servlet.http.HttpServletRequest;

public class S3Authentication implements Authentication {

    private final String accessKey;
    private final String secretKey;
    private final String signature;
    private final String dateStamp;
    private final String region;
    private final String service;
    private final String signedHeaders;
    private final HttpServletRequest request;
    private final String timestamp;
    private boolean authenticated;

    public S3Authentication(String accessKey, String secretKey) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.signature = null;
        this.dateStamp = null;
        this.region = null;
        this.service = null;
        this.signedHeaders = null;
        this.request = null;
        this.timestamp = null;
        this.authenticated = false;
    }

    public S3Authentication(String accessKey, String secretKey, String signature,
            String dateStamp, String region, String service,
            String signedHeaders, HttpServletRequest request,
            String timestamp) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.signature = signature;
        this.dateStamp = dateStamp;
        this.region = region;
        this.service = service;
        this.signedHeaders = signedHeaders;
        this.request = request;
        this.timestamp = timestamp;
        this.authenticated = false;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public Object getCredentials() {
        return secretKey;
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return accessKey;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return accessKey;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getSignature() {
        return signature;
    }

    public String getDateStamp() {
        return dateStamp;
    }

    public String getRegion() {
        return region;
    }

    public String getService() {
        return service;
    }

    public String getSignedHeaders() {
        return signedHeaders;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public String getTimestamp() {
        return timestamp;
    }
}

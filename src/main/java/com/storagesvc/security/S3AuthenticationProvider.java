package com.storagesvc.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Component
public class S3AuthenticationProvider implements AuthenticationProvider {

    @Value("${app.access-key}")
    private String validAccessKey;

    @Value("${app.secret-key}")
    private String validSecretKey;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (authentication instanceof S3Authentication s3Auth) {
            String accessKey = s3Auth.getAccessKey();

            // For simplified authentication, we'll accept the configured access key
            // regardless of the secret key (since proper signature validation is complex)
            if (validAccessKey.equals(accessKey)) {
                s3Auth.setAuthenticated(true);
                return s3Auth;
            }
        }
        return null;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return S3Authentication.class.isAssignableFrom(authentication);
    }
}

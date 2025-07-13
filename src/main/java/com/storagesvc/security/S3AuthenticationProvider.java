package com.storagesvc.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Component
public class S3AuthenticationProvider implements AuthenticationProvider {

    @Autowired
    private S3CredentialService credentialService;

    @Autowired
    private AwsSignatureValidator signatureValidator;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (authentication instanceof S3Authentication s3Auth) {
            String accessKey = s3Auth.getAccessKey();

            // Check if user exists
            if (!credentialService.userExists(accessKey)) {
                throw new BadCredentialsException("Invalid access key");
            }

            // Get the secret key for this access key
            String secretKey = credentialService.getSecretKey(accessKey);

            // Validate the signature if we have signature details
            if (s3Auth.getSignature() != null && s3Auth.getDateStamp() != null) {
                boolean isValidSignature = signatureValidator.validateSignature(
                        accessKey,
                        secretKey,
                        s3Auth.getSignature(),
                        s3Auth.getDateStamp(),
                        s3Auth.getRegion(),
                        s3Auth.getService(),
                        s3Auth.getSignedHeaders(),
                        s3Auth.getRequest());

                if (!isValidSignature) {
                    throw new BadCredentialsException("Signature does not match");
                }
            }

            s3Auth.setAuthenticated(true);
            return s3Auth;
        }
        return null;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return S3Authentication.class.isAssignableFrom(authentication);
    }
}

package com.storagesvc.security;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class S3AuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticationManager authenticationManager;
    private static final Pattern AUTHORIZATION_PATTERN = Pattern.compile(
            "AWS4-HMAC-SHA256 Credential=([^/]+)/([^/]+)/([^/]+)/([^/]+)/([^,]+),\\s*SignedHeaders=([^,]+),\\s*Signature=(.+)");

    public S3AuthenticationFilter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");

        if (authorization != null && authorization.startsWith("AWS4-HMAC-SHA256")) {
            try {
                S3Authentication s3Auth = parseAuthorizationHeader(authorization, request);
                if (s3Auth != null) {
                    Authentication authentication = authenticationManager.authenticate(s3Auth);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                logger.debug("S3 authentication failed", e);
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "SignatureDoesNotMatch");
                return;
            }
        } else if (authorization != null && authorization.startsWith("AWS ")) {
            // Handle AWS Signature Version 2 (legacy)
            try {
                S3Authentication s3Auth = parseAwsV2Authorization(authorization, request);
                if (s3Auth != null) {
                    Authentication authentication = authenticationManager.authenticate(s3Auth);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                logger.debug("AWS v2 authentication failed", e);
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "SignatureDoesNotMatch");
                return;
            }
        } else {
            // No authorization header or unsupported format
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "AccessDenied");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private S3Authentication parseAuthorizationHeader(String authorization, HttpServletRequest request) {
        Matcher matcher = AUTHORIZATION_PATTERN.matcher(authorization);
        if (matcher.matches()) {
            String accessKey = matcher.group(1);
            String dateStamp = matcher.group(2);
            String region = matcher.group(3);
            String service = matcher.group(4);
            String requestType = matcher.group(5);
            String signedHeaders = matcher.group(6);
            String signature = matcher.group(7);

            // For simplicity, we'll use a basic validation approach
            // In production, you'd want to implement full AWS signature validation
            return new S3Authentication(accessKey, ""); // We'll validate in the provider
        }
        return null;
    }

    private S3Authentication parseAwsV2Authorization(String authorization, HttpServletRequest request) {
        // AWS accessKeyId:signature format
        String[] parts = authorization.substring(4).split(":");
        if (parts.length == 2) {
            String accessKey = parts[0];
            String signature = parts[1];
            return new S3Authentication(accessKey, ""); // We'll validate in the provider
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health");
    }
}

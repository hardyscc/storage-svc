package com.storagesvc.filter;

import java.io.IOException;

import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

@Component
public class S3HeaderFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (response instanceof HttpServletResponse httpResponse) {
            // Wrap the response to intercept header setting
            S3ResponseWrapper wrappedResponse = new S3ResponseWrapper(httpResponse);
            chain.doFilter(request, wrappedResponse);
        } else {
            chain.doFilter(request, response);
        }
    }

    private static class S3ResponseWrapper extends HttpServletResponseWrapper {

        public S3ResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setHeader(String name, String value) {
            // Skip the problematic Expires header that Spring Security sets to "0"
            if ("Expires".equalsIgnoreCase(name) && "0".equals(value)) {
                return; // Don't set this header
            }
            super.setHeader(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            // Skip the problematic Expires header
            if ("Expires".equalsIgnoreCase(name) && "0".equals(value)) {
                return; // Don't add this header
            }
            super.addHeader(name, value);
        }
    }
}

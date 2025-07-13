package com.storagesvc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.storagesvc.filter.S3HeaderFilter;
import com.storagesvc.security.S3AuthenticationFilter;
import com.storagesvc.security.S3AuthenticationProvider;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final S3AuthenticationProvider s3AuthenticationProvider;

    public SecurityConfig(S3AuthenticationProvider s3AuthenticationProvider) {
        this.s3AuthenticationProvider = s3AuthenticationProvider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, S3AuthenticationFilter s3AuthenticationFilter,
            S3HeaderFilter s3HeaderFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .cacheControl(cache -> cache.disable())
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hssts -> hssts.disable()))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .authenticationProvider(s3AuthenticationProvider)
                .addFilterBefore(s3HeaderFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(s3AuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public S3AuthenticationFilter s3AuthenticationFilter(AuthenticationManager authenticationManager) {
        return new S3AuthenticationFilter(authenticationManager);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

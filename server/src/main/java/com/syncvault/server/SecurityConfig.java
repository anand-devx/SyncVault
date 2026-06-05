package com.syncvault.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 🚨 Turns off the default CSRF blocker that blocks POST requests
            .csrf(AbstractHttpConfigurer::disable) 
            // 🚨 Tells Spring Security to let EVERY request through to our custom JwtFilter!
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()); 
            
        return http.build();
    }
}
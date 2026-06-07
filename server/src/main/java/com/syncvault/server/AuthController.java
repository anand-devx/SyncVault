package com.syncvault.server;

import com.syncvault.server.service.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@RestController
public class AuthController {

    @Autowired
    private UserService userService;

    @Value("${syncvault.jwt.secret}")
    private String jwtSecret;

    @PostMapping("/auth/register")
    public ResponseEntity<String> register(@RequestParam String username,
                                           @RequestParam String password) {
        try {
            if (username.length() < 3 || password.length() < 6)
                return ResponseEntity.badRequest().body("Username min 3 chars, password min 6.");

            boolean created = userService.register(username, password);
            if (!created) return ResponseEntity.status(409).body("Username already taken.");

            return ResponseEntity.ok("Account created.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Registration failed.");
        }
    }

    @PostMapping("/auth/login")
    public ResponseEntity<String> login(@RequestParam String username,
                                        @RequestParam String password) {
        try {
            if (!userService.validate(username, password))
                return ResponseEntity.status(401).body("Invalid credentials.");

            // 🚨 FIX: Set expiration to 10 years (in milliseconds)
            // 1000L * 60s * 60m * 24h * 365d * 10y
            long tenYearsInMillis = 1000L * 60 * 60 * 24 * 365 * 10;

            String token = Jwts.builder()
                .setSubject(username)
                .setExpiration(new Date(System.currentTimeMillis() + tenYearsInMillis)) 
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .compact();

            return ResponseEntity.ok(token);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Login failed.");
        }
    }
}
package com.example.demo.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtTokenUtil {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration-minutes:120}")
    private long expirationMinutes;

    private Key getSigningKey() {
        byte[] keyBytes = decodeSecret(secretKey);
        if (keyBytes.length < 32) {
            try {
                keyBytes = MessageDigest.getInstance("SHA-256")
                        .digest(secretKey.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IllegalStateException("Unable to prepare JWT signing key", e);
            }
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private byte[] decodeSecret(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ignored) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String generateToken(String username, Long tenantId) {
        return Jwts.builder()
                .setSubject(username)
                .claim("tenantId", tenantId)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationMinutes * 60 * 1000))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256) // Use Key object and specify algorithm
                .compact();
    }

    public String generateToken(String username) {
        return generateToken(username, null);
    }
}

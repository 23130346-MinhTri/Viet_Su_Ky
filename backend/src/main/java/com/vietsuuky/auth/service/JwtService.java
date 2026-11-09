package com.vietsuuky.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                     @Value("${app.jwt.expiration-ms:900000}") long expirationMs) {
        if (secret == null || secret.isBlank() || secret.length() < 32) {
            throw new IllegalStateException("JWT secret must be configured and at least 32 characters long");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(UUID accountId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(accountId.toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public UUID extractAccountId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return UUID.fromString(claims.getSubject());
    }

    public boolean isTokenValid(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}

package com.vietsuuky.auth;

import com.vietsuuky.auth.service.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JwtServiceIntegrationTest {

    @Autowired
    private JwtService jwtService;

    @Value("${app.jwt.secret}")
    private String secret;

    @Test
    void acceptsValidSignatureAndRejectsInvalidSignatureAndExpiredToken() {
        assertThat(jwtService.isTokenValid(jwtService.generateToken(UUID.randomUUID()))).isTrue();

        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
        String invalidSignatureToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .signWith(Keys.secretKeyFor(SignatureAlgorithm.HS256))
                .compact();

        assertThat(jwtService.isTokenValid(expiredToken)).isFalse();
        assertThat(jwtService.isTokenValid(invalidSignatureToken)).isFalse();
    }
}

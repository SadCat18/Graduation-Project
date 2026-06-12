package com.skatehub.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class JwtTokenService {

    public static final String DEFAULT_SECRET = "4d6d7d6f3f6f2f7a7f5d3e6a0c8b7e9a4d6d7d6f3f6f2f7a7f5d3e6a0c8b7e9a";

    private final SecretKey key;
    private final Integer expirationHours;

    public JwtTokenService(@Value("${jwt.secret:}") String secret,
                           @Value("${jwt.expiration-hours}") Integer expirationHours,
                           Environment environment) {
        String normalizedSecret = normalizeSecret(secret, environment);
        this.key = Keys.hmacShaKeyFor(normalizedSecret.getBytes(StandardCharsets.UTF_8));
        this.expirationHours = expirationHours;
    }

    public String createToken(Long id, String role, String name) {
        return createToken(id, role, name, 0);
    }

    public String createToken(Long id, String role, String name, Integer tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(id))
                .claim("role", role)
                .claim("name", name)
                .claim("tokenVersion", tokenVersion == null ? 0 : tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    public CurrentUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long id = Long.valueOf(claims.getSubject());
        String role = claims.get("role", String.class);
        String name = claims.get("name", String.class);
        Integer tokenVersion = claims.get("tokenVersion", Integer.class);
        return new CurrentUser(id, role, name, tokenVersion == null ? 0 : tokenVersion);
    }

    private String normalizeSecret(String secret, Environment environment) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("JWT_SECRET 未配置");
        }
        String normalizedSecret = secret.trim();
        if (isProduction(environment) && DEFAULT_SECRET.equals(normalizedSecret)) {
            throw new IllegalStateException("生产环境禁止使用默认 JWT_SECRET");
        }
        return normalizedSecret;
    }

    private boolean isProduction(Environment environment) {
        return environment != null && environment.acceptsProfiles(Profiles.of("prod", "production"));
    }
}

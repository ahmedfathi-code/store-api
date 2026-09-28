package com.springtest.product_store.security;



import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Component
public class  JwtUtil {

    static final String ISSUED_AT_MILLIS_CLAIM = "iatMs";

    // الـ Secret Key - لازم يكون 32 character على الأقل (من الـ env: JWT_SECRET)
    @Value("${jwt.secret}")
    private String SECRET;

    // Access-token lifetime (default 15m); clients renew via /api/auth/refresh
    @Value("${jwt.access-token-expiration}")
    private Duration EXPIRATION;

    public Duration getAccessTokenValidity() {
        return EXPIRATION;
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    // ✅ بيعمل Token جديد
    public String generateToken(String email) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                // Unique ID (jti): without it, two logins in the same second produce identical
                // tokens, and revoking one (logout blacklist) would revoke the other session too
                .setId(UUID.randomUUID().toString())
                .setSubject(email)
                .setIssuedAt(new Date(now))
                // iat has second precision; session revocation compares in milliseconds
                .claim(ISSUED_AT_MILLIS_CLAIM, now)
                .setExpiration(new Date(now + EXPIRATION.toMillis()))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // Issue time in millis; tokens from before iatMs existed fall back to iat (seconds)
    public long getIssuedAtMillis(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        Long millis = claims.get(ISSUED_AT_MILLIS_CLAIM, Long.class);
        return millis != null ? millis : claims.getIssuedAt().getTime();
    }

    // ✅ بيجيب الإيميل من جوه الـ Token
    public String extractEmail(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    // Time left until the token expires (zero if already expired)
    public Duration getRemainingValidity(String token) {
        Date expiration = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration();
        long millis = expiration.getTime() - System.currentTimeMillis();
        return Duration.ofMillis(Math.max(0, millis));
    }

    // ✅ بيتحقق إن الـ Token صحيح وماشيش
    public boolean isTokenValid(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}
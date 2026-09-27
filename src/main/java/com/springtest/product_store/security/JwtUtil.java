package com.springtest.product_store.security;



import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.time.Duration;
import java.util.Date;

@Component
public class  JwtUtil {

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
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION.toMillis()))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
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
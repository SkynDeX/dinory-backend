package com.sstt.dinory.domain.auth.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpire;
    private final long refreshTokenExpire;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expire}") long accessTokenExpire,
            @Value("${jwt.refresh-token-expire}") long refreshTokenExpire
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpire = accessTokenExpire;
        this.refreshTokenExpire = refreshTokenExpire;
    }

    public String generateAccessToken(String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpire);

        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpire);

        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            System.out.println("✅ JWT 토큰 검증 성공!");
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            System.out.println("❌ JWT 검증 실패: Invalid JWT signature - " + e.getMessage());
            e.printStackTrace();
        } catch (ExpiredJwtException e) {
            System.out.println("❌ JWT 검증 실패: Expired JWT token - " + e.getMessage());
            System.out.println("만료 시간: " + e.getClaims().getExpiration());
            System.out.println("현재 시간: " + new Date());
        } catch (UnsupportedJwtException e) {
            System.out.println("❌ JWT 검증 실패: Unsupported JWT token - " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("❌ JWT 검증 실패: JWT claims string is empty - " + e.getMessage());
        } catch (Exception e) {
            System.out.println("❌ JWT 검증 실패: Unknown error - " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public long getRefreshTokenExpire() {
        return refreshTokenExpire;
    }
}

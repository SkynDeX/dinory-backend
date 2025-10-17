package com.sstt.dinory.service;

import com.sstt.dinory.entity.Member;
import com.sstt.dinory.entity.RefreshToken;
import com.sstt.dinory.repository.MemberRepository;
import com.sstt.dinory.repository.RefreshTokenRepository;
import com.sstt.dinory.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public String refreshAccessToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("Invalid refresh token");
        }

        RefreshToken token = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));

        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token expired");
        }

        String email = token.getMember().getEmail();
        return jwtTokenProvider.generateAccessToken(email);
    }

    @Transactional
    public void deleteRefreshToken(String refreshToken) {
        refreshTokenRepository.deleteByToken(refreshToken);
    }

    @Transactional
    public void deleteRefreshTokenByEmail(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Member not found with email: " + email));
        refreshTokenRepository.deleteByMember(member);
    }

    public RefreshToken getRefreshTokenByEmail(String email) {
        return refreshTokenRepository.findByMemberEmail(email)
                .orElseThrow(() -> new RuntimeException("Refresh token not found for email: " + email));
    }
}
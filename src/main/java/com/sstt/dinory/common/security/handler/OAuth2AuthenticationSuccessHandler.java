package com.sstt.dinory.common.security.handler;

import com.sstt.dinory.domain.auth.entity.Member;
import com.sstt.dinory.domain.auth.entity.RefreshToken;
import com.sstt.dinory.domain.auth.repository.RefreshTokenRepository;
import com.sstt.dinory.common.security.service.CustomUserDetails;
import com.sstt.dinory.domain.auth.service.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Member member = userDetails.getMember();
        String email = userDetails.getEmail();

        String accessToken = jwtTokenProvider.generateAccessToken(email);
        String refreshToken = jwtTokenProvider.generateRefreshToken(email);

        saveRefreshToken(member, refreshToken);

        addRefreshTokenCookie(response, refreshToken);

        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("accessToken", accessToken)
                .build()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private void saveRefreshToken(Member member, String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByMember(member)
                .map(existingToken -> {
                    existingToken.setToken(token);
                    existingToken.setExpiryDate(
                            LocalDateTime.now().plusSeconds(
                                    jwtTokenProvider.getRefreshTokenExpire() / 1000
                            )
                    );
                    return existingToken;
                })
                .orElseGet(() -> RefreshToken.builder()
                        .member(member)
                        .token(token)
                        .expiryDate(
                                LocalDateTime.now().plusSeconds(
                                        jwtTokenProvider.getRefreshTokenExpire() / 1000
                                )
                        )
                        .build()
                );

        refreshTokenRepository.save(refreshToken);
    }

    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        int cookieMaxAge = (int) (jwtTokenProvider.getRefreshTokenExpire() / 1000);

        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setMaxAge(cookieMaxAge);

        response.addCookie(cookie);
    }
}
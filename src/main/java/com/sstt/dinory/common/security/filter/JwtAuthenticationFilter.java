package com.sstt.dinory.common.security.filter;

import com.sstt.dinory.domain.auth.service.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

import static com.sstt.dinory.common.security.constants.SecurityConstants.*;

/**
 * JWT 인증 필터
 *
 * <p>모든 HTTP 요청에서 JWT 토큰을 검증하고 인증 정보를 SecurityContext에 설정합니다.</p>
 *
 * <h3>처리 흐름:</h3>
 * <ol>
 *   <li>공개 엔드포인트는 {@link #shouldNotFilter}에서 필터링 제외</li>
 *   <li>Authorization 헤더에서 JWT 토큰 추출</li>
 *   <li>JWT 유효성 검증 (서명, 만료 시간)</li>
 *   <li>유효한 토큰이면 SecurityContext에 인증 정보 설정</li>
 * </ol>
 *
 * @see com.sstt.dinory.common.security.constants.SecurityConstants
 * @see com.sstt.dinory.domain.auth.service.JwtTokenProvider
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 공개 엔드포인트는 JWT 검증을 건너뛰어 성능 최적화
     *
     * <p>{@link com.sstt.dinory.common.security.constants.SecurityConstants#PUBLIC_ENDPOINTS}에
     * 정의된 경로는 이 필터를 아예 거치지 않습니다.</p>
     *
     * @param request HTTP 요청
     * @return true면 필터 건너뜀, false면 필터 실행
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        boolean isPublic = Arrays.stream(PUBLIC_ENDPOINTS)
                .anyMatch(pattern -> pathMatcher.match(pattern, path));

        if (isPublic) {
            log.debug("Skipping JWT authentication for public endpoint: {}", path);
        }

        return isPublic;
    }

    /**
     * JWT 인증 처리 로직
     *
     * <p>Authorization 헤더에서 JWT를 추출하고 검증한 후,
     * 유효한 토큰이면 SecurityContext에 인증 정보를 설정합니다.</p>
     *
     * <p><strong>주의:</strong> 예외가 발생해도 필터 체인은 계속 진행됩니다.
     * 최종 접근 권한 판단은 Spring Security가 수행합니다.</p>
     *
     * @param request HTTP 요청
     * @param response HTTP 응답
     * @param filterChain 필터 체인
     * @throws ServletException 서블릿 예외
     * @throws IOException I/O 예외
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // 1. Authorization 헤더에서 JWT 추출
            String jwt = getJwtFromRequest(request);

            // 2. JWT 유효성 검증 및 인증 정보 설정
            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                String email = jwtTokenProvider.getEmailFromToken(jwt);

                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("JWT authentication successful for user: {}", email);
            }
        } catch (Exception ex) {
            log.error("JWT authentication failed: {}", ex.getMessage());
            // 예외 발생해도 계속 진행 → SecurityConfig에서 최종 판단
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Authorization 헤더에서 JWT 토큰 추출
     *
     * <p>HTTP 요청 헤더의 형식: {@code Authorization: Bearer <token>}</p>
     *
     * @param request HTTP 요청
     * @return JWT 토큰 문자열, 없으면 null
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);  // "Bearer " 제거
        }
        return null;
    }
}
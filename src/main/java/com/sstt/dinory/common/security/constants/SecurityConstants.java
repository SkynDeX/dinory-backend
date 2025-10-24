package com.sstt.dinory.common.security.constants;

/**
 * Spring Security 및 JWT 필터에서 사용하는 엔드포인트 경로 상수
 *
 * <p>모든 경로는 이 클래스에서 중앙 관리하여 SecurityConfig와 JwtAuthenticationFilter 간의
 * 불일치를 방지합니다.</p>
 *
 * <h3>사용 예시:</h3>
 * <pre>
 * // SecurityConfig.java
 * import static com.sstt.dinory.common.security.constants.SecurityConstants.*;
 *
 * .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
 *
 * // JwtAuthenticationFilter.java
 * Arrays.stream(PUBLIC_ENDPOINTS).anyMatch(...)
 * </pre>
 *
 * @author Dinory Team
 * @since 1.0
 */
public final class SecurityConstants {

    private SecurityConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }

    // ========== 공개 엔드포인트 (인증 불필요) ==========

    /**
     * 인증 관련 API
     * <ul>
     *   <li>/api/auth/login - 로그인</li>
     *   <li>/api/auth/refresh - 토큰 갱신</li>
     *   <li>/api/auth/logout - 로그아웃</li>
     *   <li>/api/auth/me - 현재 사용자 정보</li>
     *   <li>/api/auth/withdraw - 회원 탈퇴</li>
     * </ul>
     */
    public static final String AUTH_API = "/api/auth/**";

    /**
     * OAuth2 인증 플로우
     * <ul>
     *   <li>/oauth2/authorize/* - OAuth2 인증 시작</li>
     *   <li>/oauth2/callback/* - OAuth2 콜백</li>
     * </ul>
     */
    public static final String OAUTH2 = "/oauth2/**";

    /**
     * 로그인 페이지 (리다이렉트용)
     */
    public static final String LOGIN = "/login/**";

    /**
     * Swagger UI (개발 환경에서만 활성화 권장)
     * <p><strong>프로덕션 환경에서는 비활성화 필요!</strong></p>
     */
    public static final String SWAGGER_UI = "/swagger-ui/**";

    /**
     * OpenAPI 문서
     * <p><strong>프로덕션 환경에서는 비활성화 필요!</strong></p>
     */
    public static final String API_DOCS = "/v3/api-docs/**";

    /**
     * 헬스 체크 엔드포인트 (모니터링 시스템용)
     */
    public static final String HEALTH_CHECK = "/actuator/health";

    /**
     * 완전 공개 엔드포인트 배열
     * <ul>
     *   <li>Spring Security: permitAll() 적용</li>
     *   <li>JWT Filter: 검증 건너뜀 (성능 최적화)</li>
     * </ul>
     *
     * <p><strong>주의:</strong> 비즈니스 로직 엔드포인트는 여기에 추가하지 마세요!
     * 이미지, TTS, 감정 분석 등은 인증이 필요합니다.</p>
     */
    public static final String[] PUBLIC_ENDPOINTS = {
        AUTH_API,
        OAUTH2,
        LOGIN,
        SWAGGER_UI,
        API_DOCS,
        HEALTH_CHECK
    };

    // ========== 인증 필요 엔드포인트 (비즈니스 로직) ==========

    /**
     * 이미지 생성 API
     * <p>AI 기반 이미지 생성 기능 - 인증 필수</p>
     */
    public static final String IMAGE_API = "/api/image/**";

    /**
     * TTS (Text-to-Speech) 변환 API
     * <p>텍스트를 음성으로 변환 - 인증 필수</p>
     */
    public static final String TTS_API = "/api/tts/**";

    /**
     * 감정 분석 API
     * <p>사용자의 감정 상태 분석 및 저장 - 인증 필수</p>
     */
    public static final String EMOTION_API = "/api/emotion/**";

    /**
     * 자녀 관리 API
     * <p>자녀 정보 CRUD - 인증 필수</p>
     */
    public static final String CHILD_API = "/api/child/**";

    /**
     * 일기 API
     * <p>일기 작성/조회/수정/삭제 - 인증 필수</p>
     */
    public static final String DIARY_API = "/api/diary/**";

    /**
     * 동화 API
     * <p>AI 기반 동화 생성 - 인증 필수</p>
     */
    public static final String STORY_API = "/api/story/**";

    /**
     * 인증 필요 엔드포인트 배열
     * <ul>
     *   <li>Spring Security: authenticated() 적용</li>
     *   <li>JWT Filter: 검증 수행</li>
     * </ul>
     *
     * <p><strong>참고:</strong> 이 배열은 문서화 목적입니다.
     * SecurityConfig에서는 .anyRequest().authenticated()를 사용하므로
     * 명시적으로 이 배열을 사용하지 않습니다.</p>
     */
    public static final String[] AUTHENTICATED_ENDPOINTS = {
        IMAGE_API,
        TTS_API,
        EMOTION_API,
        CHILD_API,
        DIARY_API,
        STORY_API
    };

    // ========== 개발자 참고 정보 ==========

    /**
     * 프로덕션 환경에서 비활성화해야 할 엔드포인트
     */
    public static final String[] DEVELOPMENT_ONLY_ENDPOINTS = {
        SWAGGER_UI,
        API_DOCS
    };
}
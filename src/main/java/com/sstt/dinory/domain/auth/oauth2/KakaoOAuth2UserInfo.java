package com.sstt.dinory.domain.auth.oauth2;

import java.util.Map;

public class KakaoOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;
    private final Map<String, Object> kakaoAccount;
    private final Map<String, Object> profile;

    public KakaoOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
        this.kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        this.profile = (Map<String, Object>) kakaoAccount.get("profile");
    }

    @Override
    public String getProviderId() {
        return String.valueOf(attributes.get("id"));
    }

    @Override
    public String getProvider() {
        return "KAKAO";
    }

    @Override
    public String getEmail() {
        // 카카오 일반 앱은 이메일 수집 불가 (비즈니스 앱 전환 필요)
        // 이메일이 없을 경우 가짜 이메일 생성: providerId@kakao.dinory.com
        if (kakaoAccount == null || kakaoAccount.get("email") == null) {
            String providerId = String.valueOf(attributes.get("id"));
            return providerId + "@kakao.dinory.com";
        }
        return (String) kakaoAccount.get("email");
    }

    @Override
    public String getName() {
        return (String) profile.get("nickname");
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
package com.sstt.dinory.security.oauth2;

import com.sstt.dinory.entity.Member;
import com.sstt.dinory.entity.OAuthProvider;
import com.sstt.dinory.entity.Role;
import com.sstt.dinory.repository.MemberRepository;
import com.sstt.dinory.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        OAuth2UserInfo oAuth2UserInfo = getOAuth2UserInfo(registrationId, attributes);

        OAuthProvider provider = OAuthProvider.valueOf(oAuth2UserInfo.getProvider());
        String providerId = oAuth2UserInfo.getProviderId();

        Member member = memberRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> createMember(oAuth2UserInfo, provider, providerId));

        CustomUserDetails customUserDetails = new CustomUserDetails(member);
        customUserDetails.setAttributes(attributes);

        return customUserDetails;
    }

    private OAuth2UserInfo getOAuth2UserInfo(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId.toLowerCase()) {
            case "google" -> new GoogleOAuth2UserInfo(attributes);
            case "naver" -> new NaverOAuth2UserInfo(attributes);
            case "kakao" -> new KakaoOAuth2UserInfo(attributes);
            default -> throw new OAuth2AuthenticationException("Unsupported provider: " + registrationId);
        };
    }

    private Member createMember(OAuth2UserInfo oAuth2UserInfo, OAuthProvider provider, String providerId) {
        Member member = Member.builder()
                .email(oAuth2UserInfo.getEmail())
                .name(oAuth2UserInfo.getName())
                .provider(provider)
                .providerId(providerId)
                .role(Role.USER)
                .build();

        return memberRepository.save(member);
    }
}
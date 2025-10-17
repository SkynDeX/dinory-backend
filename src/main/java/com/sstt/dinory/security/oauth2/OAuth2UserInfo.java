package com.sstt.dinory.security.oauth2;

import java.util.Map;

public interface OAuth2UserInfo {

    String getProviderId();

    String getProvider();

    String getEmail();

    String getName();

    Map<String, Object> getAttributes();
}
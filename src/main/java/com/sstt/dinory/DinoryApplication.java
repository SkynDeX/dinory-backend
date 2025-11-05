package com.sstt.dinory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync  // [2025-11-04 김민중 추가] Pinecone 비동기 동기화를 위한 설정
public class DinoryApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(DinoryApplication.class);

		// 환경 변수로 프로파일 제어 (Docker에서는 SPRING_PROFILES_ACTIVE 사용)
		String activeProfiles = System.getenv("SPRING_PROFILES_ACTIVE");
		if (activeProfiles == null || activeProfiles.isEmpty()) {
			// 환경 변수가 없으면 local 개발 환경으로 설정
			app.setAdditionalProfiles("local", "secret");
		}
		// 환경 변수가 있으면 (Docker 등) 그대로 사용

		app.run(args);
	}

}

package com.sstt.dinory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync  // [2025-11-04 김민중 추가] Pinecone 비동기 동기화를 위한 설정
public class DinoryApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(DinoryApplication.class);
		app.setAdditionalProfiles("local", "secret");
		app.run(args);
	}

}

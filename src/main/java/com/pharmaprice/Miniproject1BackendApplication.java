package com.pharmaprice;

import java.util.TimeZone;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.pharmaprice.common.config.RecommendationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RecommendationProperties.class)
@EnableJpaAuditing
public class Miniproject1BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(Miniproject1BackendApplication.class, args);
	}

	@PostConstruct
	void initTimezone() {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
	}

}

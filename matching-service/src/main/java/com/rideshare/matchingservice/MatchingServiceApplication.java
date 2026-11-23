package com.rideshare.matchingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.rideshare.matchingservice.client")
public class MatchingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MatchingServiceApplication.class, args);
	}
}


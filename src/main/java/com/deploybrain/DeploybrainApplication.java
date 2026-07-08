package com.deploybrain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DeploybrainApplication {

	public static void main(String[] args) {
		SpringApplication.run(DeploybrainApplication.class, args);
	}

}

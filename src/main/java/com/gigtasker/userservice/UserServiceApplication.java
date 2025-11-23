package com.gigtasker.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UserServiceApplication {

    private UserServiceApplication() {}

	static void main(String[] args) {
		SpringApplication.run(UserServiceApplication.class, args);
	}

}

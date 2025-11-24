package com.gigtasker.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UserServiceApplication {

    private UserServiceApplication() {}

	static void main(String[] args) {
		SpringApplication.run(UserServiceApplication.class, args);
	}

}

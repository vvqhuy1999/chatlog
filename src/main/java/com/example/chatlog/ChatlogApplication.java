package com.example.chatlog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan(basePackages = {"com.example.chatlog.entity.chat"})
public class ChatlogApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatlogApplication.class, args);
	}

}

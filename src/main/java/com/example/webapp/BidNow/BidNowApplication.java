package com.example.webapp.BidNow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.CrossOrigin;


@EnableScheduling
//@EnableJpaAuditing
@EnableAsync
@EnableRetry
@CrossOrigin("http://localhost:5173")
@SpringBootApplication
public class BidNowApplication {

	public static void main(String[] args) {
		SpringApplication.run(BidNowApplication.class, args);
	}

}

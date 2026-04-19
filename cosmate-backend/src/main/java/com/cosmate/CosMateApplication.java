package com.cosmate;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.TimeZone;
import java.util.concurrent.Executor;

@SpringBootApplication(exclude = {FlywayAutoConfiguration.class})
@EnableScheduling
@EnableAsync
@EnableRetry
public class CosMateApplication {

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

	public static void main(String[] args) {
		SpringApplication.run(CosMateApplication.class, args);
	}

	@Bean(name = "taskExecutor")
	public Executor taskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(5);
		executor.setMaxPoolSize(15);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("cosmate-async-");
		executor.initialize();
		return executor;
	}

}

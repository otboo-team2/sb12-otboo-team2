package com.otboo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class OtbooRealtimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(OtbooRealtimeApplication.class, args);
    }
}

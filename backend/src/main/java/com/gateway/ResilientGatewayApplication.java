package com.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // <--- THIS IS REQUIRED
public class ResilientGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResilientGatewayApplication.class, args);
    }
}
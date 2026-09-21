package com.turkishtechnology.tierstatus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class TierStatusEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(TierStatusEngineApplication.class, args);
    }
}

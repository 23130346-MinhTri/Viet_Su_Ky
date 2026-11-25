package com.vietsuuky;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VietSuKyApplication {

    public static void main(String[] args) {
        SpringApplication.run(VietSuKyApplication.class, args);
    }
}

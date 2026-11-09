package com.mesac;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // keeps the live-update stream alive with a heartbeat
public class MesacApplication {
    public static void main(String[] args) {
        SpringApplication.run(MesacApplication.class, args);
    }
}

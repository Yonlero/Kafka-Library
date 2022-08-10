package com.learnkafka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.learnkafka.config", "com.learnkafka.consumer", "com.learnkafka.service"})
public class LibraryEventConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibraryEventConsumerApplication.class, args);
    }

}

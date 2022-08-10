package com.learnkafka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("com.learnkafka.*")
public class LibraryEventApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibraryEventApplication.class, args);
    }

}

package com.garment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
@EnableMongoAuditing
public class GarmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(GarmentApplication.class, args);
    }
}

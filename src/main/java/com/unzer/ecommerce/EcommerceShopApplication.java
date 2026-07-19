package com.unzer.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EcommerceShopApplication {
    public static void main(String[] args) {
        SpringApplication.run(EcommerceShopApplication.class, args);
    }
}
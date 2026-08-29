package com.ivanfranchin.orderapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrderApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(OrderApiApplication.class, args);
  }
}

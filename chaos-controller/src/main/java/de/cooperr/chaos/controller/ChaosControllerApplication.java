package de.cooperr.chaos.controller;

import de.cooperr.chaos.controller.config.ChaosControllerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ChaosControllerProperties.class)
public class ChaosControllerApplication {

    static void main(String[] args) {
        SpringApplication.run(ChaosControllerApplication.class, args);
    }
}

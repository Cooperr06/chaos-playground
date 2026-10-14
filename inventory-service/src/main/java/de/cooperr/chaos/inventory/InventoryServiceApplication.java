package de.cooperr.chaos.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class InventoryServiceApplication {

    static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}

package de.cooperr.chaos.shop;

import de.cooperr.chaos.shop.config.ShopProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ShopProperties.class)
public class ShopServiceApplication {

    static void main(String[] args) {
        SpringApplication.run(ShopServiceApplication.class, args);
    }
}

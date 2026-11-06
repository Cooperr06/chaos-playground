package de.cooperr.chaos.loadgenerator;

import de.cooperr.chaos.loadgenerator.config.LoadGeneratorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LoadGeneratorProperties.class)
public class LoadGeneratorApplication {

    static void main(String[] args) {
        SpringApplication.run(LoadGeneratorApplication.class, args);
    }
}

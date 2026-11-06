package de.cooperr.chaos.loadgenerator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("load-generator")
public record LoadGeneratorProperties(List<String> targetUrls, int ratePerSecond, Duration timeout) {
}

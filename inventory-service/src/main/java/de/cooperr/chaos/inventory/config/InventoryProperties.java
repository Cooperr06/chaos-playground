package de.cooperr.chaos.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties("inventory")
public record InventoryProperties(Map<String, Integer> initialStock) {
}

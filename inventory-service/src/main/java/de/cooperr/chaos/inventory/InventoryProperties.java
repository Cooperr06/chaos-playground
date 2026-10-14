package de.cooperr.chaos.inventory;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties("inventory")
public record InventoryProperties(Map<String, Integer> initialStock) {
}

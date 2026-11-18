package de.cooperr.chaos.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * @param products the products that can be reserved, every other product is unknown
 */
@ConfigurationProperties("inventory")
public record InventoryProperties(Set<String> products) {
}

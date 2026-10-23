package de.cooperr.chaos.shop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("shop")
public record ShopProperties(String inventoryUrl, String paymentUrl) {
}

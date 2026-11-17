package de.cooperr.chaos.controller.controller;

import de.cooperr.chaos.controller.config.ChaosControllerProperties;
import de.cooperr.chaos.controller.dto.UiConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/config")
public class ConfigController {

    private final UiConfig config;

    public ConfigController(ChaosControllerProperties properties) {
        this.config = new UiConfig(properties.grafanaUrl());
    }

    @GetMapping
    public UiConfig getConfig() {
        return config;
    }
}

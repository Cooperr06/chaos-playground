package de.cooperr.chaos.payment.controller;

import de.cooperr.chaos.payment.dto.ChaosSettings;
import de.cooperr.chaos.payment.service.ChaosService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chaos")
public class ChaosController {

    private final ChaosService chaosService;

    public ChaosController(ChaosService chaosService) {
        this.chaosService = chaosService;
    }

    @GetMapping
    public ChaosSettings getSettings() {
        return chaosService.getSettings();
    }

    @PostMapping
    public ChaosSettings updateSettings(@Valid @RequestBody ChaosSettings settings) {
        return chaosService.updateSettings(settings);
    }
}

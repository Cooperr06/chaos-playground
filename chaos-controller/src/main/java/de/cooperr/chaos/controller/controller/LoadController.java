package de.cooperr.chaos.controller.controller;

import de.cooperr.chaos.controller.dto.LoadSettings;
import de.cooperr.chaos.controller.service.ChaosControlService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/load")
public class LoadController {

    private final ChaosControlService chaosControlService;

    public LoadController(ChaosControlService chaosControlService) {
        this.chaosControlService = chaosControlService;
    }

    @GetMapping
    public LoadSettings getLoad() {
        return chaosControlService.getLoad();
    }

    @PostMapping
    public LoadSettings updateLoad(@Valid @RequestBody LoadSettings settings) {
        return chaosControlService.updateLoad(settings);
    }
}

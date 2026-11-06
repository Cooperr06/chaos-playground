package de.cooperr.chaos.loadgenerator.controller;

import de.cooperr.chaos.loadgenerator.dto.LoadSettings;
import de.cooperr.chaos.loadgenerator.dto.LoadStatus;
import de.cooperr.chaos.loadgenerator.service.LoadService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/load")
public class LoadController {

    private final LoadService loadService;

    public LoadController(LoadService loadService) {
        this.loadService = loadService;
    }

    @GetMapping
    public LoadStatus getStatus() {
        return loadService.getStatus();
    }

    @PostMapping
    public LoadStatus updateRate(@Valid @RequestBody LoadSettings settings) {
        return loadService.updateRate(settings.ratePerSecond());
    }
}

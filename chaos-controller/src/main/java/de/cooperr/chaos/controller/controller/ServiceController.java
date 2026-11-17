package de.cooperr.chaos.controller.controller;

import de.cooperr.chaos.controller.dto.ChaosSettings;
import de.cooperr.chaos.controller.dto.ContainerAction;
import de.cooperr.chaos.controller.dto.ServiceStatus;
import de.cooperr.chaos.controller.service.ChaosControlService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/services")
public class ServiceController {

    private final ChaosControlService chaosControlService;

    public ServiceController(ChaosControlService chaosControlService) {
        this.chaosControlService = chaosControlService;
    }

    @GetMapping
    public List<ServiceStatus> getServices() {
        return chaosControlService.getServices();
    }

    @PostMapping("/{name}/chaos")
    public ChaosSettings updateChaos(@PathVariable String name, @Valid @RequestBody ChaosSettings settings) {
        return chaosControlService.updateChaos(name, settings);
    }

    @PostMapping("/{name}/start")
    public ServiceStatus start(@PathVariable String name) {
        return chaosControlService.changeContainer(name, ContainerAction.START);
    }

    @PostMapping("/{name}/stop")
    public ServiceStatus stop(@PathVariable String name) {
        return chaosControlService.changeContainer(name, ContainerAction.STOP);
    }

    @PostMapping("/{name}/pause")
    public ServiceStatus pause(@PathVariable String name) {
        return chaosControlService.changeContainer(name, ContainerAction.PAUSE);
    }

    @PostMapping("/{name}/unpause")
    public ServiceStatus unpause(@PathVariable String name) {
        return chaosControlService.changeContainer(name, ContainerAction.UNPAUSE);
    }
}

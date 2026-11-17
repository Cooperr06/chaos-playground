package de.cooperr.chaos.controller.controller;

import de.cooperr.chaos.controller.config.ChaosControllerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConfigController.class)
@EnableConfigurationProperties(ChaosControllerProperties.class)
@TestPropertySource(properties = "chaos-controller.grafana-url=/grafana")
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getConfig_grafanaUrlSet_returnsGrafanaUrl() throws Exception {
        mockMvc.perform(get("/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grafanaUrl").value("/grafana"));
    }
}

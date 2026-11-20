package de.cooperr.chaos.controller.controller;

import de.cooperr.chaos.controller.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static de.cooperr.chaos.controller.config.AutheliaHeaders.admin;
import static de.cooperr.chaos.controller.config.AutheliaHeaders.viewer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeController.class)
@Import(SecurityConfig.class)
class MeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getCurrentUser_viewer_returnsNotAdmin() throws Exception {
        mockMvc.perform(get("/me").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("guest"))
                .andExpect(jsonPath("$.admin").value(false));
    }

    @Test
    void getCurrentUser_admin_returnsAdmin() throws Exception {
        mockMvc.perform(get("/me").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("prof"))
                .andExpect(jsonPath("$.admin").value(true));
    }

    @Test
    void getCurrentUser_noHeaders_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/me"))
                .andExpect(status().isUnauthorized());
    }
}

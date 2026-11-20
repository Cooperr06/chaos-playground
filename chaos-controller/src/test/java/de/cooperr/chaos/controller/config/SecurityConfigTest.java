package de.cooperr.chaos.controller.config;

import de.cooperr.chaos.controller.controller.LoadController;
import de.cooperr.chaos.controller.controller.ServiceController;
import de.cooperr.chaos.controller.dto.ContainerAction;
import de.cooperr.chaos.controller.dto.ContainerState;
import de.cooperr.chaos.controller.dto.LoadSettings;
import de.cooperr.chaos.controller.dto.ServiceStatus;
import de.cooperr.chaos.controller.service.ChaosControlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static de.cooperr.chaos.controller.config.AutheliaHeaders.admin;
import static de.cooperr.chaos.controller.config.AutheliaHeaders.user;
import static de.cooperr.chaos.controller.config.AutheliaHeaders.viewer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ServiceController.class, LoadController.class})
@Import(SecurityConfig.class)
class SecurityConfigTest {

    private static final String PAYMENT = "payment-service";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChaosControlService chaosControlService;

    @Test
    void getServices_noUserHeader_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/services"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getServices_noGroupsHeader_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/services").header(SecurityConfig.USER_HEADER, "guest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getServices_viewer_returnsOk() throws Exception {
        when(chaosControlService.getServices()).thenReturn(List.of());

        mockMvc.perform(get("/services").with(viewer()))
                .andExpect(status().isOk());
    }

    @Test
    void getServices_unknownGroupOnly_returnsForbidden() throws Exception {
        mockMvc.perform(get("/services").with(user("guest", "dev")))
                .andExpect(status().isForbidden());
    }

    @Test
    void stop_viewer_returnsForbidden() throws Exception {
        mockMvc.perform(post("/services/{name}/stop", PAYMENT).with(viewer()).with(csrf()))
                .andExpect(status().isForbidden());

        verify(chaosControlService, never()).changeContainer(anyString(), any());
    }

    @Test
    void stop_admin_returnsOk() throws Exception {
        when(chaosControlService.changeContainer(PAYMENT, ContainerAction.STOP))
                .thenReturn(new ServiceStatus(PAYMENT, ContainerState.EXITED, null));

        mockMvc.perform(post("/services/{name}/stop", PAYMENT).with(admin()).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void stop_adminInSeveralGroups_returnsOk() throws Exception {
        when(chaosControlService.changeContainer(PAYMENT, ContainerAction.STOP))
                .thenReturn(new ServiceStatus(PAYMENT, ContainerState.EXITED, null));

        mockMvc.perform(post("/services/{name}/stop", PAYMENT).with(user("prof", "dev, chaos-admin")).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void stop_adminWithoutCsrfToken_returnsForbidden() throws Exception {
        mockMvc.perform(post("/services/{name}/stop", PAYMENT).with(admin()))
                .andExpect(status().isForbidden());

        verify(chaosControlService, never()).changeContainer(anyString(), any());
    }

    @Test
    void updateLoad_viewer_returnsForbidden() throws Exception {
        String requestBody = """
                {"ratePerSecond": %d}
                """.formatted(10);

        mockMvc.perform(post("/load")
                        .with(viewer())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());

        verify(chaosControlService, never()).updateLoad(any(LoadSettings.class));
    }
}

package de.cooperr.chaos.controller.config;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Adds the headers Traefik sets after Authelia has checked the login, for tests with MockMvc.
 * Public because the controller tests live in another package.
 */
public final class AutheliaHeaders {

    private AutheliaHeaders() {
    }

    public static RequestPostProcessor admin() {
        return user("prof", "chaos-admin");
    }

    public static RequestPostProcessor viewer() {
        return user("guest", "chaos-viewer");
    }

    public static RequestPostProcessor user(String name, String groups) {
        return request -> {
            request.addHeader(SecurityConfig.USER_HEADER, name);
            request.addHeader(SecurityConfig.GROUPS_HEADER, groups);
            return request;
        };
    }
}

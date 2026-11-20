package de.cooperr.chaos.controller.controller;

import de.cooperr.chaos.controller.config.SecurityConfig;
import de.cooperr.chaos.controller.dto.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me")
public class MeController {

    private static final String ADMIN_AUTHORITY = "ROLE_%s".formatted(SecurityConfig.ADMIN_ROLE);

    @GetMapping
    public CurrentUser getCurrentUser(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_AUTHORITY::equals);
        return new CurrentUser(authentication.getName(), admin);
    }
}

package de.cooperr.chaos.controller.config;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Login comes from Authelia: Traefik asks Authelia for every request and passes the user and their groups on as
 * headers. The controller trusts these headers, so it must only be reachable through Traefik.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    static final String USER_HEADER = "Remote-User";
    static final String GROUPS_HEADER = "Remote-Groups";

    public static final String ADMIN_ROLE = "ADMIN";
    static final String VIEWER_ROLE = "VIEWER";

    private final Map<String, String> roleByGroup;

    public SecurityConfig(AuthProperties authProperties) {
        this.roleByGroup = Map.of(
                authProperties.adminGroup(), ADMIN_ROLE,
                authProperties.viewerGroup(), VIEWER_ROLE
        );
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        PreAuthenticatedAuthenticationProvider authenticationProvider = new PreAuthenticatedAuthenticationProvider();
        // without a groups header the provider does not call this, the request stays anonymous
        authenticationProvider.setPreAuthenticatedUserDetailsService(this::toUser);

        RequestHeaderAuthenticationFilter headerFilter = createHeaderFilter(authenticationProvider);

        return http
                .addFilterAt(headerFilter, AbstractPreAuthenticatedProcessingFilter.class)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // token in the cookie XSRF-TOKEN, app.js sends it back in the header X-XSRF-TOKEN
                .csrf(CsrfConfigurer::spa)
                // without this, a missing login is answered with 403 instead of 401
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        // Prometheus and the CI smoke test call these directly, Traefik does not route /actuator
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.POST, "/services/**", "/load").hasRole(ADMIN_ROLE)
                        .requestMatchers(HttpMethod.GET, "/**").hasAnyRole(VIEWER_ROLE, ADMIN_ROLE)
                        .anyRequest().denyAll())
                .build();
    }

    private static @NonNull RequestHeaderAuthenticationFilter createHeaderFilter(
            PreAuthenticatedAuthenticationProvider authenticationProvider) {
        RequestHeaderAuthenticationFilter headerFilter = new RequestHeaderAuthenticationFilter();
        headerFilter.setPrincipalRequestHeader(USER_HEADER);
        // the groups travel as "credentials" to toUser
        headerFilter.setCredentialsRequestHeader(GROUPS_HEADER);
        headerFilter.setExceptionIfHeaderMissing(false);
        // the default stores the login in an HTTP session, every request brings its headers anyway
        headerFilter.setSecurityContextRepository(new RequestAttributeSecurityContextRepository());
        headerFilter.setAuthenticationManager(new ProviderManager(authenticationProvider));
        return headerFilter;
    }

    private UserDetails toUser(PreAuthenticatedAuthenticationToken token) {
        // never null here: without a groups header the provider does not call this method
        String groupsHeader = (String) Objects.requireNonNull(token.getCredentials());
        String[] roles = Arrays.stream(groupsHeader.split(","))
                .map(String::trim)
                // groups without a role are ignored
                .filter(roleByGroup::containsKey)
                .map(roleByGroup::get)
                .distinct()
                .toArray(String[]::new);

        return User.withUsername(token.getName())
                .password("")
                .roles(roles)
                .build();
    }
}

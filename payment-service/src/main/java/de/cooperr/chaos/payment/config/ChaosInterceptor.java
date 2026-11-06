package de.cooperr.chaos.payment.config;

import de.cooperr.chaos.payment.service.ChaosService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

public class ChaosInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ChaosInterceptor.class);

    private final ChaosService chaosService;

    public ChaosInterceptor(ChaosService chaosService) {
        this.chaosService = chaosService;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        log.debug("Handling request for method {} with path {}", request.getMethod(), request.getRequestURI());

        chaosService.injectChaos();
        return true;
    }
}

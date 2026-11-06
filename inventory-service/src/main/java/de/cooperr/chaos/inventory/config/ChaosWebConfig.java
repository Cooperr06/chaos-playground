package de.cooperr.chaos.inventory.config;

import de.cooperr.chaos.inventory.service.ChaosService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ChaosWebConfig implements WebMvcConfigurer {

    private final ChaosService chaosService;

    public ChaosWebConfig(ChaosService chaosService) {
        this.chaosService = chaosService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ChaosInterceptor(chaosService)).addPathPatterns("/inventory/**");
    }
}

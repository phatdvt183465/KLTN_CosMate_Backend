package com.cosmate.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import java.util.Arrays;
import java.util.List;

@Configuration
public class GlobalCorsConfig {

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOriginsProperty;

    @Bean(name = "globalCorsConfigurationSource")
    public UrlBasedCorsConfigurationSource globalCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // parse allowed origins; support comma separated or a single '*'
        if (allowedOriginsProperty != null && !allowedOriginsProperty.isBlank()) {
            String[] parts = allowedOriginsProperty.split(",");
            for (String p : parts) {
                String v = p.trim();
                if (!v.isEmpty()) {
                    configuration.addAllowedOriginPattern(v);
                }
            }
        } else {
            configuration.addAllowedOriginPattern("*");
        }

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean(name = "globalCorsFilter")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public FilterRegistrationBean<CorsFilter> globalCorsFilterRegistration() {
        UrlBasedCorsConfigurationSource source = globalCorsConfigurationSource();
        CorsFilter corsFilter = new CorsFilter(source);
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(corsFilter);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}

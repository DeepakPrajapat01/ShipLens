package com.aichangeguard.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Web MVC Configuration.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    // Custom interceptors or converters can be registered here as API routes evolve
}

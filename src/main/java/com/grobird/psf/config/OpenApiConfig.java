package com.grobird.psf.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Sets the OpenAPI server URL to "/" so Swagger UI uses the same origin as the page
 * (e.g. https://presalesforce.ai). Fixes "URL scheme must be http or https" and
 * "Failed to fetch" when trying out APIs from Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        Server server = new Server();
        server.setUrl("/");
        server.setDescription("Current origin (same as this page)");
        return new OpenAPI().servers(List.of(server));
    }
}

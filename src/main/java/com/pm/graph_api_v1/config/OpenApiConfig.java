package com.pm.graph_api_v1.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI graphApiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Graph API v1")
                        .description("Graph explorer backend: resolve, one-hop, and shortest-path.")
                        .version("v1")
                        .license(new License().name("Proprietary")));
    }
}

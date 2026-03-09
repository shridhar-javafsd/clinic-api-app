package com.demo.clinic.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI clinicOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Clinic Management API")
                        .description("REST API for managing doctors, patients, and appointments. " +
                                "Instrumented with Micrometer for Prometheus metrics.")
                        .version("2.0.0")
                        .contact(new Contact()
                                .name("The Skill Enhancers")
                                .email("training@skillenhancers.com"))
                        .license(new License()
                                .name("Apache 2.0")));
    }
}

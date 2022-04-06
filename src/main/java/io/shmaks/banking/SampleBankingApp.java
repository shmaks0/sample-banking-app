package io.shmaks.banking;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.web.reactive.config.EnableWebFlux;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableWebFlux
@OpenAPIDefinition(info = @Info(title = "Sample Banking API", version = "v0.0.1"))
@SecurityScheme(
        name = "basicAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "basic"
)
public class SampleBankingApp {
    public static void main(String[] args) {
        SpringApplication.run(SampleBankingApp.class, args);
    }
}

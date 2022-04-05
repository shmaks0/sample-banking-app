package io.shmaks.banking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.web.reactive.config.EnableWebFlux;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableWebFlux
public class SampleBankingApp {
    public static void main(String[] args) {
        SpringApplication.run(SampleBankingApp.class, args);
    }
}

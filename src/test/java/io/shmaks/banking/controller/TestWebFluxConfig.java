package io.shmaks.banking.controller;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import java.util.Base64;

@Configuration
public class TestWebFluxConfig implements WebFluxConfigurer {

    static final String USER_OWNER_ID = "alice";
    static final String OTHER_OWNER_ID = "bob";
    static final String ADMIN_OWNER_ID = "admin";
    static final String PRIVILEGED_CLIENT_ID = "REPORTING";

    static final String USER_TOKEN = "Dummy " + Base64.getEncoder().encodeToString(("someClientId_" + USER_OWNER_ID).getBytes());
    static final String OTHER_USER_TOKEN = "Dummy " + Base64.getEncoder().encodeToString(("someClientId_" + OTHER_OWNER_ID).getBytes());
    static final String ADMIN_TOKEN = "Dummy " + Base64.getEncoder().encodeToString((PRIVILEGED_CLIENT_ID + "_" + ADMIN_OWNER_ID).getBytes());

}

package io.shmaks.banking.controller;

import java.util.Base64;

public class TestHelper {

    static final String USER_OWNER_ID = "alice";
    static final String OTHER_OWNER_ID = "bob";
    static final String ADMIN_OWNER_ID = "admin";
    static final String PRIVILEGED_CLIENT_ID = "REPORTING";

    static final String USER_TOKEN = "Basic " + Base64.getEncoder().encodeToString(("someClientId:" + USER_OWNER_ID).getBytes());
    static final String OTHER_USER_TOKEN = "Basic " + Base64.getEncoder().encodeToString(("someClientId:" + OTHER_OWNER_ID).getBytes());
    static final String ADMIN_TOKEN = "Basic " + Base64.getEncoder().encodeToString((PRIVILEGED_CLIENT_ID + ":" + ADMIN_OWNER_ID).getBytes());

}

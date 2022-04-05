package io.shmaks.banking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

import java.util.Set;

@ConfigurationProperties("sample-banking-app")
@ConstructorBinding
public class SampleAppProps {

    public static final SampleAppProps DEFAULT = new SampleAppProps(
            Set.of("John", "shmaks"), "BANKING_ADMIN", "reportingApp"
    );

    private final Set<String> users;
    private final String admin;
    private final String privilegedClientId;

    public SampleAppProps(Set<String> users, String admin, String privilegedClientId) {
        this.users = users != null ? users : DEFAULT.users;
        this.admin = admin != null ? admin : DEFAULT.admin;
        this.privilegedClientId = privilegedClientId != null ? privilegedClientId : DEFAULT.privilegedClientId;
    }

    public Set<String> getUsers() {
        return users;
    }

    public String getAdmin() {
        return admin;
    }

    public String getPrivilegedClientId() {
        return privilegedClientId;
    }
}

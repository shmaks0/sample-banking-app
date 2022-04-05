package io.shmaks.banking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.springframework.http.HttpHeaders;

import java.util.Base64;
import java.util.Collection;
import java.util.Set;

@Configuration
@EnableGlobalMethodSecurity
@EnableWebFluxSecurity
public class SecurityConfig {

    private final SampleAppProps appProps;

    public SecurityConfig(SampleAppProps appProps) {
        this.appProps = appProps;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http.httpBasic().disable();
        http.formLogin().disable();
        http.csrf().disable();
        http.logout().disable();

        return http.authorizeExchange()
                .anyExchange().authenticated()
                .and().build();
    }

    @Bean
    public ReactiveAuthenticationManager authManager() {
        return authentication -> {
            if (authentication.isAuthenticated()) {
                return Mono.just(authentication);
            } else {
                return Mono.error(new UsernameNotFoundException("User not found"));
            }
        };
    }

    @Bean
    public ServerSecurityContextRepository serverSecurityContextRepository() {
        return new ServerSecurityContextRepository() {

            @Override
            public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
                return Mono.empty();
            }

            @Override
            public Mono<SecurityContext> load(ServerWebExchange exchange) {
                var authHeader = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
                if (authHeader == null || authHeader.size() != 1 || !authHeader.get(0).startsWith("Dummy ")) {
                    return Mono.error(new AuthenticationCredentialsNotFoundException("No authentication"));
                }

                var decodedToken = new String(Base64.getDecoder().decode(authHeader.get(0).substring("Dummy ".length())));
                var split = decodedToken.split("_");
                if (split.length != 2) {
                    return  Mono.error(new AuthenticationCredentialsNotFoundException("Bad authentication"));
                }

                if (appProps.getPrivilegedClientId().equals(split[0]) && appProps.getAdmin().equals(split[1])) {
                    return Mono.just(new SecurityContextImpl(DummyAuthenticationToken.adminToken(split[0], split[1])));
                } else if (appProps.getUsers().contains(split[1])) {
                    return Mono.just(new SecurityContextImpl(DummyAuthenticationToken.userToken(split[0], split[1])));
                } else {
                    return Mono.just(new SecurityContextImpl(DummyAuthenticationToken.unknown(split[0], split[1])));
                }
            }
        };
    }

    private static class DummyAuthenticationToken extends PreAuthenticatedAuthenticationToken {

        private static final GrantedAuthority USER = new SimpleGrantedAuthority("USER");
        private static final GrantedAuthority ADMIN = new SimpleGrantedAuthority("ADMIN");

        private DummyAuthenticationToken(Object aPrincipal, Object aCredentials) {
            super(aPrincipal, aCredentials);
        }

        private DummyAuthenticationToken(Object aPrincipal, Object aCredentials, Collection<? extends GrantedAuthority> anAuthorities) {
            super(aPrincipal, aCredentials, anAuthorities);
        }

        public static DummyAuthenticationToken userToken(String clientId, String ownerId) {
            return new DummyAuthenticationToken(ownerId, clientId, Set.of(USER));
        }

        public static DummyAuthenticationToken adminToken(String clientId, String ownerId) {
            return new DummyAuthenticationToken(ownerId, clientId, Set.of(ADMIN, USER));
        }

        public static DummyAuthenticationToken unknown(String clientId, String ownerId) {
            return new DummyAuthenticationToken(ownerId, clientId);
        }
    }
}

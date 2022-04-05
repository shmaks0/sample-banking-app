package io.shmaks.banking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.shmaks.banking.config.AppConfig;
import io.shmaks.banking.config.SampleAppExtProps;
import io.shmaks.banking.config.SampleAppProps;
import io.shmaks.banking.config.SecurityConfig;
import io.shmaks.banking.model.Account;
import io.shmaks.banking.model.AccountType;
import io.shmaks.banking.repo.InMemoryAccountRepo;
import io.shmaks.banking.service.bookkeeping.OrgAccountsBootstrapper;
import io.shmaks.banking.service.dto.AccountResponse;
import io.shmaks.banking.service.dto.CreateAccountRequest;
import org.apache.logging.log4j.util.Strings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.shmaks.banking.controller.TestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@WebFluxTest(AccountController.class)
@Import({AppConfig.class, SecurityConfig.class})
@EnableConfigurationProperties({SampleAppProps.class, SampleAppExtProps.class})
@TestPropertySource(properties = {
        "sample-banking-app.users[0]=" + USER_OWNER_ID,
        "sample-banking-app.users[1]=" + OTHER_OWNER_ID,
        "sample-banking-app.admin=" + ADMIN_OWNER_ID,
        "sample-banking-app.privilegedClientId=" + PRIVILEGED_CLIENT_ID
})
public class AccountControllerTest {

    @Autowired
    ObjectMapper jackson;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    InMemoryAccountRepo repo;

    @Autowired
    WebTestClient testClient;

    @Autowired
    OrgAccountsBootstrapper orgAccountsBootstrapper;

    @AfterEach
    void cleanup() {
        repo.clear();
        orgAccountsBootstrapper.bootstrap();
    }

    @Test
    void createAccount() throws Exception {
        var badCurrencyRequest = jackson.writeValueAsString(
                new CreateAccountRequest(BigDecimal.TEN, "RUB", null)
        );

        testClient.post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(badCurrencyRequest)
                .exchange()
                .expectStatus().isBadRequest();

        var badBalanceRequest = jackson.writeValueAsString(
                new CreateAccountRequest(BigDecimal.TEN.negate(), "AED", null)
        );

        testClient.post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(badBalanceRequest)
                .exchange()
                .expectStatus().isBadRequest();

        var badDisplayNameRequest = jackson.writeValueAsString(
                new CreateAccountRequest(BigDecimal.TEN, "AED", Strings.repeat("a", 257))
        );

        testClient.post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(badDisplayNameRequest)
                .exchange()
                .expectStatus().isBadRequest();

        var request = jackson.writeValueAsString(
                new CreateAccountRequest(BigDecimal.TEN, "AED", "john first account")
        );

        //try to create by unknown user
        testClient.post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();

        StepVerifier.create(repo.findAllUserAccountsOrderByAccountNumberAsc(1, null))
                .expectNextMatches(List::isEmpty)
                .verifyComplete();

        var responseSpec = testClient.post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated();

        var accounts = repo.findAllUserAccountsOrderByAccountNumberAsc(1, null).block();
        assertThat(accounts)
                .hasSize(1)
                .first()
                .returns(BigDecimal.TEN, Account::getBalance)
                .returns("AED", Account::getCurrencyCode)
                .returns("john first account", Account::getDisplayedName)
                .returns(AccountType.USER, Account::getType)
                .returns(USER_OWNER_ID, Account::getOwnerId)
                .returns(null, Account::getDeletedAt)
                .returns(null, Account::getLastTxnId)
                .matches(account ->
                        account.getId() != null && account.getAccountNumber() != null && account.getCreatedAt() != null
                );

        responseSpec.expectHeader().location("/accounts/" + Objects.requireNonNull(accounts).get(0).getId() + "/balance");
    }

    @Test
    void deleteAccount() throws Exception {
        var request = jackson.writeValueAsString(
                new CreateAccountRequest(null, "AED", null)
        );

        testClient.post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated();

        var accountId = Objects.requireNonNull(repo.findAllUserAccountsOrderByAccountNumberAsc(1, null).block()).get(0).getId();

        //try to delete by other user
        testClient
                .delete()
                .uri("/accounts/" + accountId)
                .header(HttpHeaders.AUTHORIZATION, OTHER_USER_TOKEN)
                .exchange()
                .expectStatus().isNoContent();

        StepVerifier.create(repo.findAllUserAccountsOrderByAccountNumberAsc(1, null))
                .expectNextMatches(accounts -> accounts.size() == 1)
                .verifyComplete();

        //try to delete by unknown user
        testClient
                .delete()
                .uri("/accounts/" + accountId)
                .exchange()
                .expectStatus().isUnauthorized();

        StepVerifier.create(repo.findAllUserAccountsOrderByAccountNumberAsc(1, null))
                .expectNextMatches(accounts -> accounts.size() == 1)
                .verifyComplete();

        testClient.delete()
                .uri("/accounts/" + accountId)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .exchange()
                .expectStatus().isNoContent();

        StepVerifier.create(repo.findAllUserAccountsOrderByAccountNumberAsc(1, null))
                .expectNextMatches(List::isEmpty)
                .verifyComplete();

        testClient.delete()
                .uri("/accounts/" + accountId)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .exchange()
                .expectStatus().isNoContent();

        assertThat(repo.getAccounts().stream().filter(it -> it.getType() == AccountType.USER))
                .hasSize(1)
                .first()
                .returns(BigDecimal.ZERO, Account::getBalance)
                .returns("AED", Account::getCurrencyCode)
                .returns(null, Account::getDisplayedName)
                .returns(AccountType.USER, Account::getType)
                .returns(USER_OWNER_ID, Account::getOwnerId)
                .returns(null, Account::getLastTxnId)
                .matches(account ->
                        account.getId() != null && account.getAccountNumber() != null && account.getCreatedAt() != null
                )
                .matches(account -> account.getDeletedAt() != null);
    }

    @Test
    void getBalance() throws Exception {
        var balance = 1234.567;

        var request = jackson.writeValueAsString(
                new CreateAccountRequest(BigDecimal.valueOf(balance), "AED", null)
        );

        testClient.post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated();

        var accountId = Objects.requireNonNull(repo.findAllUserAccountsOrderByAccountNumberAsc(1, null).block()).get(0).getId();

        //try to get by other user
        testClient
                .get()
                .uri("/accounts/" + accountId + "/balance")
                .header(HttpHeaders.AUTHORIZATION, OTHER_USER_TOKEN)
                .exchange()
                .expectStatus().isNotFound();

        //try to get by unknown user
        testClient
                .get()
                .uri("/accounts/" + accountId + "/balance")
                .exchange()
                .expectStatus().isUnauthorized();

        testClient.get()
                .uri("/accounts/" + accountId + "/balance")
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody().json("{ \"amount\": " + balance + " }");

        var emptyBalanceRequest = jackson.writeValueAsString(
                new CreateAccountRequest(null, "AED", null)
        );

        repo.clear();
        testClient.post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(emptyBalanceRequest)
                .exchange()
                .expectStatus().isCreated();

        accountId = Objects.requireNonNull(repo.findAllUserAccountsOrderByAccountNumberAsc(1, null).block()).get(0).getId();

        testClient.get()
                .uri("/accounts/" + accountId + "/balance")
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody().json("{ \"amount\": 0 }");
    }

    @Test
    void listAccounts() throws Exception {

        var account1 = jackson.writeValueAsString(
                new CreateAccountRequest(BigDecimal.TEN, "AED", "acc1")
        );
        var account2 = jackson.writeValueAsString(
                new CreateAccountRequest(null, "USD", "acc2")
        );
        var account3 = jackson.writeValueAsString(
                new CreateAccountRequest(BigDecimal.ONE, "EUR", "acc3")
        );

        testClient.get()
                .uri("/accounts")
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody().json("[]");

        testClient.post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(account1)
                .exchange()
                .expectStatus().isCreated();
        testClient.post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(account2)
                .exchange()
                .expectStatus().isCreated();
        testClient.post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(account3)
                .exchange()
                .expectStatus().isCreated();

        //try to get by other user
        testClient
                .get()
                .uri("/accounts")
                .header(HttpHeaders.AUTHORIZATION, OTHER_USER_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody().json("[]");

        //try to get by unknown user
        testClient
                .get()
                .uri("/accounts")
                .exchange()
                .expectStatus().isUnauthorized();

        var allAccounts = repo.getAccounts()
                .stream()
                .filter(it -> it.getType() == AccountType.USER)
                .map(AccountResponse::new)
                .collect(Collectors.toList());

        testClient.get()
                .uri("/accounts")
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody().json(jackson.writeValueAsString(allAccounts));

        testClient.get()
                .uri("/accounts?count=21")
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .exchange()
                .expectStatus().isBadRequest();

        testClient.get()
                .uri("/accounts?count=2")
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody().json(jackson.writeValueAsString(allAccounts.subList(0, 2)));

        testClient.get()
                .uri("/accounts?count=1")
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody().json(jackson.writeValueAsString(List.of(allAccounts.get(0))));

        testClient.get()
                .uri("/accounts?count=1&after=" + allAccounts.get(0).getAccountNumber())
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody().json(jackson.writeValueAsString(List.of(allAccounts.get(1))));

        testClient.get()
                .uri("/accounts?count=2&after=" + allAccounts.get(1).getAccountNumber())
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody().json(jackson.writeValueAsString(List.of(allAccounts.get(2))));

        testClient.get()
                .uri("/accounts?count=10&after=" + allAccounts.get(2).getAccountNumber())
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody().json("[]");

        testClient.get()
                .uri("/accounts?after=" + allAccounts.get(2).getAccountNumber() + "0")
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody()
                .json(jackson.writeValueAsString(allAccounts));

        repo.deleteByIdAndOwnerId(allAccounts.get(1).getId(), USER_OWNER_ID).block();

        testClient.get()
                .uri("/accounts")
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody()
                .json(jackson.writeValueAsString(List.of(allAccounts.get(0), allAccounts.get(2))));

        testClient.get()
                .uri("/accounts?after=" + allAccounts.get(1).getAccountNumber())
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody()
                .json(jackson.writeValueAsString(List.of(allAccounts.get(2))));
    }

    @Test
    void listAllAccounts() throws Exception {

        var account1 = jackson.writeValueAsString(
                new CreateAccountRequest(BigDecimal.TEN, "AED", "owner1acc1")
        );
        var account2 = jackson.writeValueAsString(
                new CreateAccountRequest(null, "USD", "owner2acc1")
        );
        var account3 = jackson.writeValueAsString(
                new CreateAccountRequest(BigDecimal.ONE, "EUR", "owner1acc2")
        );
        var account4 = jackson.writeValueAsString(
                new CreateAccountRequest(BigDecimal.valueOf(101), "AED", "owner1acc3")
        );

        testClient.get()
                .uri("/accounts/all")
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, ADMIN_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody().json("[]");

        testClient
                .post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(account1)
                .exchange()
                .expectStatus().isCreated();
        testClient
                .post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, OTHER_USER_TOKEN)
                .bodyValue(account2)
                .exchange()
                .expectStatus().isCreated();
        testClient
                .post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(account3)
                .exchange()
                .expectStatus().isCreated();
        testClient
                .post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(account4)
                .exchange()
                .expectStatus().isCreated();

        //try to get by a user
        testClient
                .get()
                .uri("/accounts/all")
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .exchange()
                .expectStatus().isForbidden();

        //try to get by unknown user
        testClient
                .get()
                .uri("/accounts/all")
                .exchange()
                .expectStatus().isUnauthorized();

        var allAccounts = repo.getAccounts()
                .stream()
                .filter(it -> it.getType() == AccountType.USER)
                .map(AccountResponse::new)
                .collect(Collectors.toList());

        testClient.get()
                .uri("/accounts/all")
                .header(HttpHeaders.AUTHORIZATION, ADMIN_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody().json(jackson.writeValueAsString(allAccounts));

        testClient.get()
                .uri("/accounts/all?count=51")
                .header(HttpHeaders.AUTHORIZATION, ADMIN_TOKEN)
                .exchange()
                .expectStatus().isBadRequest();

        testClient.get()
                .uri("/accounts/all?count=2")
                .header(HttpHeaders.AUTHORIZATION, ADMIN_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody().json(jackson.writeValueAsString(allAccounts.subList(0, 2)));

        testClient.get()
                .uri("/accounts/all?count=1")
                .header(HttpHeaders.AUTHORIZATION, ADMIN_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody().json(jackson.writeValueAsString(List.of(allAccounts.get(0))));

        testClient.get()
                .uri("/accounts/all?count=1&after=" + allAccounts.get(0).getAccountNumber())
                .header(HttpHeaders.AUTHORIZATION, ADMIN_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody().json(jackson.writeValueAsString(List.of(allAccounts.get(1))));

        testClient.get()
                .uri("/accounts/all?count=2&after=" + allAccounts.get(1).getAccountNumber())
                .header(HttpHeaders.AUTHORIZATION, ADMIN_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody().json(jackson.writeValueAsString(allAccounts.subList(2, 4)));

        testClient.get()
                .uri("/accounts/all?count=10&after=" + allAccounts.get(2).getAccountNumber())
                .header(HttpHeaders.AUTHORIZATION, ADMIN_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody().json(jackson.writeValueAsString(List.of(allAccounts.get(3))));

        testClient.get()
                .uri("/accounts/all?after=" + allAccounts.get(3).getAccountNumber() + "0")
                .header(HttpHeaders.AUTHORIZATION, ADMIN_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody()
                .json(jackson.writeValueAsString(allAccounts));

        repo.deleteByIdAndOwnerId(allAccounts.get(2).getId(), USER_OWNER_ID).block();

        testClient.get()
                .uri("/accounts/all")
                .header(HttpHeaders.AUTHORIZATION, ADMIN_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody()
                .json(jackson.writeValueAsString(List.of(allAccounts.get(0), allAccounts.get(1), allAccounts.get(3))));

        testClient.get()
                .uri("/accounts/all?after=" + allAccounts.get(1).getAccountNumber())
                .header(HttpHeaders.AUTHORIZATION, ADMIN_TOKEN)
                .exchange()
                .expectStatus().isOk().expectBody()
                .json(jackson.writeValueAsString(List.of(allAccounts.get(3))));
    }
}

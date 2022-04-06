package io.shmaks.banking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.shmaks.banking.config.AppConfig;
import io.shmaks.banking.config.SampleAppExtProps;
import io.shmaks.banking.config.SampleAppProps;
import io.shmaks.banking.config.SecurityConfig;
import io.shmaks.banking.ext.CurrencyPair;
import io.shmaks.banking.ext.FeeService;
import io.shmaks.banking.model.*;
import io.shmaks.banking.repo.InMemoryAccountRepo;
import io.shmaks.banking.repo.InMemoryTxnGroupRepo;
import io.shmaks.banking.repo.InMemoryTxnRepo;
import io.shmaks.banking.service.AccountService;
import io.shmaks.banking.service.bookkeeping.CorrespondentAccountsBootstrapper;
import io.shmaks.banking.service.bookkeeping.OrgAccountsBootstrapper;
import io.shmaks.banking.service.dto.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.ThrowingConsumer;
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
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static io.shmaks.banking.controller.TestHelper.*;
import static io.shmaks.banking.controller.TransferControllerTest.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@WebFluxTest(TransferController.class)
@Import({AppConfig.class, SecurityConfig.class})
@EnableConfigurationProperties({SampleAppProps.class, SampleAppExtProps.class})
@TestPropertySource(properties = {
        "sample-banking-app.users[0]=" + USER_OWNER_ID,
        "sample-banking-app.users[1]=" + OTHER_OWNER_ID,
        "sample-banking-app.admin=" + ADMIN_OWNER_ID,
        "sample-banking-app.privilegedClientId=" + PRIVILEGED_CLIENT_ID,
        "sample-banking-app.ext.currenciesExchangeRates[0].from=AED",
        "sample-banking-app.ext.currenciesExchangeRates[0].to=USD",
        "sample-banking-app.ext.currenciesExchangeRates[0].rate=" + AED_2_USD,
        "sample-banking-app.ext.currenciesExchangeRates[0].reverseRate=" + USD_2_AED,
        "sample-banking-app.ext.currenciesExchangeRates[1].from=AED",
        "sample-banking-app.ext.currenciesExchangeRates[1].to=EUR",
        "sample-banking-app.ext.currenciesExchangeRates[1].rate=" + AED_2_EUR,
        "sample-banking-app.ext.currenciesExchangeRates[1].reverseRate=" + EUR_2_AED,
        "sample-banking-app.ext.currenciesExchangeRates[2].from=USD",
        "sample-banking-app.ext.currenciesExchangeRates[2].to=EUR",
        "sample-banking-app.ext.currenciesExchangeRates[2].rate=" + USD_2_EUR,
        "sample-banking-app.ext.currenciesExchangeRates[2].reverseRate=" + EUR_2_USD,
        "sample-banking-app.ext.correspondentOwners[0]=" + SBERBANK,
        "sample-banking-app.ext.correspondentOwners[1]=" + BARCLAYS,
})
public class TransferControllerTest {

    static final double AED_2_USD = 0.2;
    static final double USD_2_AED = 4;
    static final double AED_2_EUR = 0.1;
    static final double EUR_2_AED = 9;
    static final double USD_2_EUR = 0.5;
    static final double EUR_2_USD = 1.5;

    static final String SBERBANK = "SBER";
    static final String BARCLAYS = "BARC";

    @Autowired
    ObjectMapper jackson;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    InMemoryAccountRepo accountRepo;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    InMemoryTxnGroupRepo txnGroupRepo;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    InMemoryTxnRepo txnRepo;

    @Autowired
    WebTestClient testClient;

    @Autowired
    OrgAccountsBootstrapper orgAccountsBootstrapper;

    @Autowired
    CorrespondentAccountsBootstrapper correspondentAccountsBootstrapper;

    @Autowired
    AccountService accountService;

    @Autowired
    FeeService feeService;

    @AfterEach
    void cleanup() {
        accountRepo.clear();
        txnRepo.clear();
        txnGroupRepo.clear();
        orgAccountsBootstrapper.bootstrap();
        correspondentAccountsBootstrapper.bootstrap();
    }

    @Test
    void sameCurrencyDeposit() throws Exception {

        var accountRequest = new CreateAccountRequest(BigDecimal.TEN, "AED", null);
        var badAccountDepositRequest = new DepositRequest("unknown", BigDecimal.TEN, "AED", "#1");
        var txnUuid = UUID.randomUUID();
        // todo: validation
        testClient.put()
                .uri("/transfer/deposit/" + txnUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(badAccountDepositRequest)
                .exchange()
                .expectStatus().isBadRequest();

        var acc = Objects.requireNonNull(accountService.create(USER_OWNER_ID, accountRequest).block());
        var depositRequest = new DepositRequest(acc.getAccountNumber(), BigDecimal.TEN, "AED", "#1");
        var balanceByAccNumber = accountRepo.getAccounts().stream()
                .collect(Collectors.toMap(Account::getAccountNumber, Account::getBalance));
        StepVerifier.create(txnGroupRepo.findByUUID(txnUuid)).verifyComplete();

        Callable<TxnResult> round = () -> {
            // try by other users
            testClient.put()
                    .uri("/transfer/deposit/" + txnUuid)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, OTHER_USER_TOKEN)
                    .bodyValue(depositRequest)
                    .exchange()
                    .expectStatus().isBadRequest();
            testClient.put()
                    .uri("/transfer/deposit/" + txnUuid)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, ADMIN_TOKEN)
                    .bodyValue(depositRequest)
                    .exchange()
                    .expectStatus().isBadRequest();

            var responseSpec = testClient.put()
                    .uri("/transfer/deposit/" + txnUuid)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                    .bodyValue(depositRequest)
                    .exchange()
                    .expectStatus().isOk();

            var account = Objects.requireNonNull(accountRepo.findById(acc.getId()).block());
            assertThat(account.getLastTxnId()).isNotNull();

            var txnGroup = txnGroupRepo.findByUUID(txnUuid).block();
            assertThat(txnGroup).isNotNull()
                    .returns(BigDecimal.TEN, TxnGroup::getAmount)
                    .returns("AED", TxnGroup::getCurrencyCode)
                    .returns(TxnType.DEPOSIT, TxnGroup::getType)
                    .returns("#1", TxnGroup::getComment)
                    .returns(null, TxnGroup::getPayerAccountNumber)
                    .returns(account.getAccountNumber(), TxnGroup::getReceiverAccountNumber)
                    .returns(txnUuid, TxnGroup::getTxnUUID)
                    .matches(txn -> txn.getCreatedAt() != null);

            var txn = txnRepo.findByTxnGroupIdAndAccountIdAndSpendingType(
                    txnGroup.getId(), account.getId(), TxnSpendingType.TRANSFER
            ).block();
            assertThat(txn).isNotNull()
                    .returns(account.getId(), Txn::getAccountId)
                    .returns(BigDecimal.TEN, Txn::getAmount)
                    .returns(TxnStatus.SUCCESS, Txn::getStatus)
                    .matches(t -> t.getLinkingTxnId() != null && t.getCreatedAt() != null && t.getDetails() != null);
            var linkedTxn = txnRepo.findById(txn.getLinkingTxnId());
            assertThat(linkedTxn)
                    .isNotNull()
                    .returns(txn.getId(), Txn::getLinkingTxnId)
                    .returns(BigDecimal.TEN.negate(), Txn::getAmount);
            var linkedAccount = accountRepo.findById(linkedTxn.getAccountId()).block();

            var expectedBody = new TxnResult(txn, account);

            responseSpec.expectBody().json(jackson.writeValueAsString(expectedBody));

            var newBalancesByNumber = accountRepo.getAccounts().stream()
                    .collect(Collectors.toMap(Account::getAccountNumber, Account::getBalance));
            assertThat(newBalancesByNumber.get(account.getAccountNumber()))
                    .isEqualTo(balanceByAccNumber.get(account.getAccountNumber()).add(BigDecimal.TEN));
            assertThat(newBalancesByNumber.get(Objects.requireNonNull(linkedAccount).getAccountNumber()))
                    .isEqualTo(balanceByAccNumber.get(linkedAccount.getAccountNumber()).subtract(BigDecimal.TEN));
            newBalancesByNumber.remove(account.getAccountNumber());
            newBalancesByNumber.remove(linkedAccount.getAccountNumber());
            assertThat(newBalancesByNumber).allSatisfy((other, amount) ->
                    assertThat(balanceByAccNumber.get(other)).isEqualTo(amount)
            );

            return expectedBody;
        };

        var set = new HashSet<TxnResult>();
        for (int i = 0; i < 3; i++) {
            set.add(round.call());
        }
        assertThat(set).hasSize(1);

        var newTxnUuid = UUID.randomUUID();
        var newDepositRequest = new DepositRequest(acc.getAccountNumber(), BigDecimal.valueOf(150.15), "AED", "#2");

        testClient.put()
                .uri("/transfer/deposit/" + newTxnUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(newDepositRequest)
                .exchange()
                .expectStatus().isOk();

        var newBalancesByNumber = accountRepo.getAccounts().stream()
                .collect(Collectors.toMap(Account::getAccountNumber, Account::getBalance));
        var delta = depositRequest.getAmount().add(newDepositRequest.getAmount());
        assertThat(newBalancesByNumber.get(acc.getAccountNumber()))
                .isEqualTo(balanceByAccNumber.get(acc.getAccountNumber()).add(delta));
        var linkedAccount = accountRepo.findById(
                txnRepo.findById(txnRepo.findById(acc.getLastTxnId()).getLinkingTxnId()).getAccountId()
        ).block();
        assertThat(newBalancesByNumber.get(Objects.requireNonNull(linkedAccount).getAccountNumber()))
                .isEqualTo(balanceByAccNumber.get(linkedAccount.getAccountNumber()).subtract(delta));
        newBalancesByNumber.remove(acc.getAccountNumber());
        newBalancesByNumber.remove(linkedAccount.getAccountNumber());
        assertThat(newBalancesByNumber).allSatisfy((other, amount) ->
                assertThat(balanceByAccNumber.get(other)).isEqualTo(amount)
        );
    }

    @Test
    void otherCurrencyDeposit() throws Exception {

        var accountRequest = new CreateAccountRequest(BigDecimal.TEN, "AED", null);
        var txnUuid = UUID.randomUUID();

        var acc = Objects.requireNonNull(accountService.create(USER_OWNER_ID, accountRequest).block());
        var depositRequest = new DepositRequest(acc.getAccountNumber(), BigDecimal.TEN, "USD", "#1");
        var totalBalanceByCurrency = accountRepo.getAccounts().stream()
                .collect(Collectors.toMap(Account::getCurrencyCode, Account::getBalance, BigDecimal::add));

        var fee = feeService.getExchangeFee(new CurrencyPair("USD", "AED"), depositRequest.getAmount()).block();

        Callable<TxnResult> round = () -> {
            var responseSpec = testClient.put()
                    .uri("/transfer/deposit/" + txnUuid)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                    .bodyValue(depositRequest)
                    .exchange()
                    .expectStatus().isOk();

            var account = Objects.requireNonNull(accountRepo.findById(acc.getId()).block());
            assertThat(account.getLastTxnId()).isNotNull();

            var txnGroup = txnGroupRepo.findByUUID(txnUuid).block();
            assertThat(txnGroup).isNotNull()
                    .returns(depositRequest.getAmount(), TxnGroup::getAmount)
                    .returns(depositRequest.getCurrencyCode(), TxnGroup::getCurrencyCode)
                    .returns(TxnType.DEPOSIT, TxnGroup::getType)
                    .returns("#1", TxnGroup::getComment)
                    .returns(null, TxnGroup::getPayerAccountNumber)
                    .returns(account.getAccountNumber(), TxnGroup::getReceiverAccountNumber)
                    .returns(txnUuid, TxnGroup::getTxnUUID)
                    .matches(txn -> txn.getCreatedAt() != null);

            var delta = depositRequest.getAmount().subtract(fee).multiply(BigDecimal.valueOf(USD_2_AED));

            var txn = txnRepo.findById(account.getLastTxnId());
            assertThat(txn).isNotNull()
                    .returns(account.getId(), Txn::getAccountId)
                    .returns(delta, Txn::getAmount)
                    .returns(TxnStatus.SUCCESS, Txn::getStatus)
                    .matches(t -> t.getLinkingTxnId() != null && t.getCreatedAt() != null && t.getDetails() != null);
            var linkedTxn = txnRepo.findById(txn.getLinkingTxnId());
            assertThat(linkedTxn)
                    .isNotNull()
                    .returns(txn.getId(), Txn::getLinkingTxnId)
                    .returns(delta.negate(), Txn::getAmount);
            var expectedBody = new TxnResult(txn, account);

            responseSpec.expectBody().json(jackson.writeValueAsString(expectedBody));

            var newTotalBalanceByCurrency = accountRepo.getAccounts().stream()
                    .collect(Collectors.toMap(Account::getCurrencyCode, Account::getBalance, BigDecimal::add));
            assertThat(acc.getBalance())
                    .isEqualTo(accountRequest.getInitialBalance().add(delta));
            assertThat(newTotalBalanceByCurrency.get("EUR")).isEqualTo(totalBalanceByCurrency.get("EUR"));
            assertThat(newTotalBalanceByCurrency.get("USD"))
                    .isEqualTo(totalBalanceByCurrency.get("USD").subtract(depositRequest.getAmount().subtract(fee)));
            assertThat(newTotalBalanceByCurrency.get("AED"))
                    .isEqualTo(totalBalanceByCurrency.get("AED").add(delta));

            return expectedBody;
        };

        var set = new HashSet<TxnResult>();
        for (int i = 0; i < 3; i++) {
            set.add(round.call());
        }
        assertThat(set).hasSize(1);

        var newTxnUuid = UUID.randomUUID();
        var newDepositRequest = new DepositRequest(acc.getAccountNumber(), BigDecimal.valueOf(42.42), "EUR", "#2");

        testClient.put()
                .uri("/transfer/deposit/" + newTxnUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(newDepositRequest)
                .exchange()
                .expectStatus().isOk();

        var newFee = feeService.getExchangeFee(new CurrencyPair("EUR", "AED"), newDepositRequest.getAmount()).block();
        var delta = depositRequest.getAmount().subtract(fee).multiply(BigDecimal.valueOf(USD_2_AED))
                .add(newDepositRequest.getAmount().subtract(newFee).multiply(BigDecimal.valueOf(EUR_2_AED)));

        var newTotalBalanceByCurrency = accountRepo.getAccounts().stream()
                .collect(Collectors.toMap(Account::getCurrencyCode, Account::getBalance, BigDecimal::add));
        assertThat(acc.getBalance())
                .isEqualTo(accountRequest.getInitialBalance().add(delta));
        assertThat(newTotalBalanceByCurrency.get("EUR"))
                .isEqualTo(totalBalanceByCurrency.get("EUR").subtract(newDepositRequest.getAmount().subtract(newFee)));
        assertThat(newTotalBalanceByCurrency.get("USD"))
                .isEqualTo(totalBalanceByCurrency.get("USD").subtract(depositRequest.getAmount().subtract(fee)));
        assertThat(newTotalBalanceByCurrency.get("AED"))
                .isEqualTo(totalBalanceByCurrency.get("AED").add(delta));

    }

    @Test
    void sameCurrencyWithdrawal() throws Exception {

        var accountRequest = new CreateAccountRequest(BigDecimal.valueOf(100), "AED", null);
        var badAccountWithdrawalRequest = new WithdrawalRequest("unknown", BigDecimal.TEN, "AED", "#1");
        var txnUuid = UUID.randomUUID();
        // todo: validation
        testClient.put()
                .uri("/transfer/withdrawal/" + txnUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(badAccountWithdrawalRequest)
                .exchange()
                .expectStatus().isBadRequest();

        var acc = Objects.requireNonNull(accountService.create(USER_OWNER_ID, accountRequest).block());
        var withdrawalRequest = new WithdrawalRequest(acc.getAccountNumber(), BigDecimal.valueOf(40), "AED", "recurrent");
        var balanceByAccNumber = accountRepo.getAccounts().stream()
                .collect(Collectors.toMap(Account::getAccountNumber, Account::getBalance));
        StepVerifier.create(txnGroupRepo.findByUUID(txnUuid)).verifyComplete();

        Callable<TxnResult> round = () -> {
            // try by other users
            testClient.put()
                    .uri("/transfer/withdrawal/" + txnUuid)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, OTHER_USER_TOKEN)
                    .bodyValue(withdrawalRequest)
                    .exchange()
                    .expectStatus().isBadRequest();
            testClient.put()
                    .uri("/transfer/withdrawal/" + txnUuid)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, ADMIN_TOKEN)
                    .bodyValue(withdrawalRequest)
                    .exchange()
                    .expectStatus().isBadRequest();

            var responseSpec = testClient.put()
                    .uri("/transfer/withdrawal/" + txnUuid)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                    .bodyValue(withdrawalRequest)
                    .exchange()
                    .expectStatus().isOk();

            var account = Objects.requireNonNull(accountRepo.findById(acc.getId()).block());
            assertThat(account.getLastTxnId()).isNotNull();

            var txnGroup = txnGroupRepo.findByUUID(txnUuid).block();
            assertThat(txnGroup).isNotNull()
                    .returns(withdrawalRequest.getAmount(), TxnGroup::getAmount)
                    .returns("AED", TxnGroup::getCurrencyCode)
                    .returns(TxnType.WITHDRAWAL, TxnGroup::getType)
                    .returns(withdrawalRequest.getComment(), TxnGroup::getComment)
                    .returns(null, TxnGroup::getReceiverAccountNumber)
                    .returns(account.getAccountNumber(), TxnGroup::getPayerAccountNumber)
                    .returns(txnUuid, TxnGroup::getTxnUUID)
                    .matches(txn -> txn.getCreatedAt() != null);

            var txn = txnRepo.findByTxnGroupIdAndAccountIdAndSpendingType(
                    txnGroup.getId(), account.getId(), TxnSpendingType.TRANSFER
            ).block();
            assertThat(txn).isNotNull()
                    .returns(account.getId(), Txn::getAccountId)
                    .returns(withdrawalRequest.getAmount().negate(), Txn::getAmount)
                    .returns(TxnStatus.SUCCESS, Txn::getStatus)
                    .matches(t -> t.getLinkingTxnId() != null && t.getCreatedAt() != null && t.getDetails() != null);
            var linkedTxn = txnRepo.findById(txn.getLinkingTxnId());
            assertThat(linkedTxn)
                    .isNotNull()
                    .returns(txn.getId(), Txn::getLinkingTxnId)
                    .returns(withdrawalRequest.getAmount(), Txn::getAmount);
            var linkedAccount = accountRepo.findById(linkedTxn.getAccountId()).block();

            var expectedBody = new TxnResult(txn, account);

            responseSpec.expectBody().json(jackson.writeValueAsString(expectedBody));

            var delta = withdrawalRequest.getAmount();

            var newBalancesByNumber = accountRepo.getAccounts().stream()
                    .collect(Collectors.toMap(Account::getAccountNumber, Account::getBalance));
            assertThat(newBalancesByNumber.get(account.getAccountNumber()))
                    .isEqualTo(balanceByAccNumber.get(account.getAccountNumber()).subtract(delta));
            assertThat(newBalancesByNumber.get(Objects.requireNonNull(linkedAccount).getAccountNumber()))
                    .isEqualTo(balanceByAccNumber.get(linkedAccount.getAccountNumber()).add(delta));
            newBalancesByNumber.remove(account.getAccountNumber());
            newBalancesByNumber.remove(linkedAccount.getAccountNumber());
            assertThat(newBalancesByNumber).allSatisfy((other, amount) ->
                    assertThat(balanceByAccNumber.get(other)).isEqualTo(amount)
            );

            return expectedBody;
        };

        var set = new HashSet<TxnResult>();
        for (int i = 0; i < 3; i++) {
            set.add(round.call());
        }
        assertThat(set).hasSize(1);

        testClient.put()
                .uri("/transfer/withdrawal/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(withdrawalRequest)
                .exchange()
                .expectStatus().isOk();

        Runnable twoWithdrawalsTookPlace = () -> {
            var newBalancesByNumber = accountRepo.getAccounts().stream()
                    .collect(Collectors.toMap(Account::getAccountNumber, Account::getBalance));
            var delta = withdrawalRequest.getAmount().multiply(BigDecimal.valueOf(2));
            assertThat(newBalancesByNumber.get(acc.getAccountNumber()))
                    .isEqualTo(balanceByAccNumber.get(acc.getAccountNumber()).subtract(delta));
            var linkedAccount = accountRepo.findById(
                    txnRepo.findById(txnRepo.findById(acc.getLastTxnId()).getLinkingTxnId()).getAccountId()
            ).block();
            assertThat(newBalancesByNumber.get(Objects.requireNonNull(linkedAccount).getAccountNumber()))
                    .isEqualTo(balanceByAccNumber.get(linkedAccount.getAccountNumber()).add(delta));
            newBalancesByNumber.remove(acc.getAccountNumber());
            newBalancesByNumber.remove(linkedAccount.getAccountNumber());
            assertThat(newBalancesByNumber).allSatisfy((other, amount) ->
                    assertThat(balanceByAccNumber.get(other)).isEqualTo(amount)
            );
        };
        twoWithdrawalsTookPlace.run();

        testClient.put()
                .uri("/transfer/withdrawal/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(withdrawalRequest)
                .exchange()
                .expectStatus().isBadRequest();

        twoWithdrawalsTookPlace.run();
    }

    @Test
    void otherCurrencyWithdrawal() throws Exception {

        var accountRequest = new CreateAccountRequest(BigDecimal.valueOf(1000), "AED", null);
        var txnUuid = UUID.randomUUID();

        var acc = Objects.requireNonNull(accountService.create(USER_OWNER_ID, accountRequest).block());
        var withdrawalRequest = new WithdrawalRequest(acc.getAccountNumber(), BigDecimal.valueOf(50), "USD", null);
        var totalBalanceByCurrency = accountRepo.getAccounts().stream()
                .collect(Collectors.toMap(Account::getCurrencyCode, Account::getBalance, BigDecimal::add));

        var exchanged = withdrawalRequest.getAmount().divide(BigDecimal.valueOf(AED_2_USD), RoundingMode.HALF_UP);

        var fee = feeService.getExchangeFee(new CurrencyPair("AED", "USD"), exchanged).block();

        Callable<TxnResult> round = () -> {
            var responseSpec = testClient.put()
                    .uri("/transfer/withdrawal/" + txnUuid)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                    .bodyValue(withdrawalRequest)
                    .exchange()
                    .expectStatus().isOk();

            var account = Objects.requireNonNull(accountRepo.findById(acc.getId()).block());
            assertThat(account.getLastTxnId()).isNotNull();

            var txnGroup = txnGroupRepo.findByUUID(txnUuid).block();
            assertThat(txnGroup).isNotNull()
                    .returns(withdrawalRequest.getAmount(), TxnGroup::getAmount)
                    .returns(withdrawalRequest.getCurrencyCode(), TxnGroup::getCurrencyCode)
                    .returns(TxnType.WITHDRAWAL, TxnGroup::getType)
                    .returns(null, TxnGroup::getComment)
                    .returns(null, TxnGroup::getReceiverAccountNumber)
                    .returns(account.getAccountNumber(), TxnGroup::getPayerAccountNumber)
                    .returns(txnUuid, TxnGroup::getTxnUUID)
                    .matches(txn -> txn.getCreatedAt() != null);

            var delta = exchanged.add(fee);

            var txn = txnRepo.findById(account.getLastTxnId());
            assertThat(txn).isNotNull()
                    .returns(account.getId(), Txn::getAccountId)
                    .returns(delta.negate(), Txn::getAmount)
                    .returns(TxnStatus.SUCCESS, Txn::getStatus)
                    .matches(t -> t.getLinkingTxnId() != null && t.getCreatedAt() != null && t.getDetails() != null);
            var linkedTxn = txnRepo.findById(txn.getLinkingTxnId());
            assertThat(linkedTxn)
                    .isNotNull()
                    .returns(txn.getId(), Txn::getLinkingTxnId)
                    .returns(delta, Txn::getAmount);
            var expectedBody = new TxnResult(txn, account);

            responseSpec.expectBody().json(jackson.writeValueAsString(expectedBody));

            var newTotalBalanceByCurrency = accountRepo.getAccounts().stream()
                    .collect(Collectors.toMap(Account::getCurrencyCode, Account::getBalance, BigDecimal::add));
            assertThat(acc.getBalance())
                    .isEqualTo(accountRequest.getInitialBalance().subtract(delta));
            assertThat(newTotalBalanceByCurrency.get("EUR")).isEqualTo(totalBalanceByCurrency.get("EUR"));
            assertThat(newTotalBalanceByCurrency.get("USD"))
                    .isEqualTo(totalBalanceByCurrency.get("USD").add(withdrawalRequest.getAmount()));
            assertThat(newTotalBalanceByCurrency.get("AED"))
                    .isEqualTo(totalBalanceByCurrency.get("AED").subtract(exchanged));

            return expectedBody;
        };

        var set = new HashSet<TxnResult>();
        for (int i = 0; i < 3; i++) {
            set.add(round.call());
        }
        assertThat(set).hasSize(1);

        var newWithdrawalRequest = new WithdrawalRequest(acc.getAccountNumber(), BigDecimal.valueOf(70), "EUR", "#2");

        testClient.put()
                .uri("/transfer/withdrawal/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(newWithdrawalRequest)
                .exchange()
                .expectStatus().isOk();

        var newExchanged = newWithdrawalRequest.getAmount().divide(BigDecimal.valueOf(AED_2_EUR), RoundingMode.HALF_UP);
        var newFee = feeService.getExchangeFee(new CurrencyPair("AED", "EUR"), newExchanged).block();
        var delta = exchanged.add(fee).add(newExchanged).add(newFee);

        var newTotalBalanceByCurrency = accountRepo.getAccounts().stream()
                .collect(Collectors.toMap(Account::getCurrencyCode, Account::getBalance, BigDecimal::add));
        assertThat(acc.getBalance())
                .isEqualTo(accountRequest.getInitialBalance().subtract(delta));
        assertThat(newTotalBalanceByCurrency.get("EUR"))
                .isEqualTo(totalBalanceByCurrency.get("EUR").add(newWithdrawalRequest.getAmount()));
        assertThat(newTotalBalanceByCurrency.get("USD"))
                .isEqualTo(totalBalanceByCurrency.get("USD").add(withdrawalRequest.getAmount()));
        assertThat(newTotalBalanceByCurrency.get("AED"))
                .isEqualTo(totalBalanceByCurrency.get("AED").subtract(exchanged).subtract(newExchanged));

        var lastResort = new WithdrawalRequest(acc.getAccountNumber(), BigDecimal.valueOf(5), "EUR", "#3");

        testClient.put()
                .uri("/transfer/withdrawal/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(lastResort)
                .exchange()
                .expectStatus().isBadRequest();

        lastResort = new WithdrawalRequest(acc.getAccountNumber(), BigDecimal.valueOf(5), "USD", "#3");

        testClient.put()
                .uri("/transfer/withdrawal/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(lastResort)
                .exchange()
                .expectStatus().isOk();

        assertThat(acc.getBalance()).isNotNegative();
    }

    @Test
    void sameCurrencyTransfer() throws Exception {

        var aliceAccountRequest = new CreateAccountRequest(BigDecimal.valueOf(100), "AED", null);
        var aliceAccount = Objects.requireNonNull(accountService.create(USER_OWNER_ID, aliceAccountRequest).block());
        var bobAccountRequest = new CreateAccountRequest(null, "AED", null);
        var badAccountTransferRequest1 = new TransferRequest("unknown", aliceAccount.getAccountNumber(), BigDecimal.TEN, "bad");
        var badAccountTransferRequest2 = new TransferRequest(aliceAccount.getAccountNumber(), "unknown", BigDecimal.TEN, "bad");

        var txnUuid = UUID.randomUUID();
        // todo: validation
        assertThat(List.of(badAccountTransferRequest1, badAccountTransferRequest2)).allSatisfy(bad ->
                testClient.put()
                        .uri("/transfer/" + txnUuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                        .bodyValue(bad)
                        .exchange()
                        .expectStatus().isBadRequest()
        );

        var alice2AliceTransfer = new TransferRequest(
                aliceAccount.getAccountNumber(), aliceAccount.getAccountNumber(), BigDecimal.valueOf(10), "alice2alice");
        testClient.put()
                .uri("/transfer/" + txnUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(alice2AliceTransfer)
                .exchange()
                .expectStatus().isBadRequest();

        var sberAedNumber = getOrgAccount("AED", AccountType.CORRESPONDENT, SBERBANK).getAccountNumber();
        var alice2SberTransfer = new TransferRequest(
                aliceAccount.getAccountNumber(), sberAedNumber, BigDecimal.valueOf(10), "alice2alice");
        testClient.put()
                .uri("/transfer/" + txnUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(alice2SberTransfer)
                .exchange()
                .expectStatus().isBadRequest();

        var bobAccount = Objects.requireNonNull(accountService.create(OTHER_OWNER_ID, bobAccountRequest).block());
        var bob2AliceTransfer = new TransferRequest(
                bobAccount.getAccountNumber(), aliceAccount.getAccountNumber(), BigDecimal.valueOf(60.35), "bob2alice");
        var alice2BobTransfer = new TransferRequest(
                aliceAccount.getAccountNumber(), bobAccount.getAccountNumber(), BigDecimal.valueOf(60.35), "alice2bob");
        var balanceByAccNumber = accountRepo.getAccounts().stream()
                .collect(Collectors.toMap(Account::getAccountNumber, Account::getBalance));
        StepVerifier.create(txnGroupRepo.findByUUID(txnUuid)).verifyComplete();

        Callable<Boolean> round = () -> {
            var txnUUID1 = UUID.randomUUID();
            var txnUUID2 = UUID.randomUUID();

            //try by with insufficient
            testClient.put()
                    .uri("/transfer/" + txnUUID1)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, OTHER_USER_TOKEN)
                    .bodyValue(bob2AliceTransfer)
                    .exchange()
                    .expectStatus().isBadRequest();
            // try by receiver
            testClient.put()
                    .uri("/transfer/" + txnUUID1)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, OTHER_USER_TOKEN)
                    .bodyValue(alice2BobTransfer)
                    .exchange()
                    .expectStatus().isBadRequest();
            // try by third party
            testClient.put()
                    .uri("/transfer/" + txnUUID1)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, ADMIN_TOKEN)
                    .bodyValue(alice2BobTransfer)
                    .exchange()
                    .expectStatus().isBadRequest();

            Runnable lend = () -> testClient.put()
                    .uri("/transfer/" + txnUUID1)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                    .bodyValue(alice2BobTransfer)
                    .exchange()
                    .expectStatus().isOk().expectBody()
                    .jsonPath("accountId").isEqualTo(aliceAccount.getId())
                    .jsonPath("amount").isEqualTo(alice2BobTransfer.getAmount().negate());
            Runnable returnDebt = () -> testClient.put()
                    .uri("/transfer/" + txnUUID2)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, OTHER_USER_TOKEN)
                    .bodyValue(bob2AliceTransfer)
                    .exchange()
                    .expectStatus().isOk().expectBody()
                    .jsonPath("accountId").isEqualTo(bobAccount.getId())
                    .jsonPath("amount").isEqualTo(bob2AliceTransfer.getAmount().negate());
            lend.run();

            assertThat(Objects.requireNonNull(accountRepo.findById(aliceAccount.getId()).block()).getLastTxnId()).isNotNull();

            var txnGroup = txnGroupRepo.findByUUID(txnUUID1).block();
            assertThat(txnGroup).isNotNull()
                    .returns(alice2BobTransfer.getAmount(), TxnGroup::getAmount)
                    .returns("AED", TxnGroup::getCurrencyCode)
                    .returns(TxnType.TRANSFER, TxnGroup::getType)
                    .returns(alice2BobTransfer.getComment(), TxnGroup::getComment)
                    .returns(aliceAccount.getAccountNumber(), TxnGroup::getPayerAccountNumber)
                    .returns(bobAccount.getAccountNumber(), TxnGroup::getReceiverAccountNumber)
                    .returns(txnUUID1, TxnGroup::getTxnUUID)
                    .matches(txn -> txn.getCreatedAt() != null);

            var aliceTxn = txnRepo.findByTxnGroupIdAndAccountIdAndSpendingType(
                    txnGroup.getId(), aliceAccount.getId(), TxnSpendingType.TRANSFER
            ).block();
            var bobTxn = txnRepo.findByTxnGroupIdAndAccountIdAndSpendingType(
                    txnGroup.getId(), bobAccount.getId(), TxnSpendingType.TRANSFER
            ).block();
            assertThat(aliceTxn).isNotNull()
                    .returns(aliceAccount.getId(), Txn::getAccountId)
                    .returns(alice2BobTransfer.getAmount().negate(), Txn::getAmount)
                    .returns(TxnStatus.SUCCESS, Txn::getStatus)
                    .returns(Objects.requireNonNull(bobTxn).getId(), Txn::getLinkingTxnId)
                    .matches(t -> t.getCreatedAt() != null && t.getDetails() != null);
            assertThat(bobTxn).isNotNull()
                    .returns(bobAccount.getId(), Txn::getAccountId)
                    .returns(alice2BobTransfer.getAmount(), Txn::getAmount)
                    .returns(TxnStatus.SUCCESS, Txn::getStatus)
                    .returns(aliceTxn.getId(), Txn::getLinkingTxnId);

            lend.run();

            var delta = alice2BobTransfer.getAmount();

            var newBalancesByNumber = accountRepo.getAccounts().stream()
                    .collect(Collectors.toMap(Account::getAccountNumber, Account::getBalance));
            assertThat(newBalancesByNumber.get(aliceAccount.getAccountNumber()))
                    .isEqualTo(balanceByAccNumber.get(aliceAccount.getAccountNumber()).subtract(delta));
            assertThat(newBalancesByNumber.get(bobAccount.getAccountNumber()))
                    .isEqualTo(balanceByAccNumber.get(bobAccount.getAccountNumber()).add(delta));
            newBalancesByNumber.remove(aliceAccount.getAccountNumber());
            newBalancesByNumber.remove(bobAccount.getAccountNumber());
            assertThat(newBalancesByNumber).allSatisfy((other, amount) ->
                    assertThat(balanceByAccNumber.get(other)).isEqualTo(amount)
            );

            returnDebt.run();

            testClient.put()
                    .uri("/transfer/" + txnUUID2)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                    .bodyValue(bob2AliceTransfer)
                    .exchange()
                    .expectStatus().isBadRequest();

            newBalancesByNumber = accountRepo.getAccounts().stream()
                    .collect(Collectors.toMap(Account::getAccountNumber, Account::getBalance));
            assertThat(newBalancesByNumber).allSatisfy((other, amount) ->
                    assertThat(balanceByAccNumber.get(other)).isEqualByComparingTo(amount)
            );

            return true;
        };

        for (int i = 0; i < 3; i++) {
            round.call();
        }
    }

    @Test
    void otherCurrencyTransfer() throws Throwable {

        var aliceAccountRequest = new CreateAccountRequest(BigDecimal.valueOf(100), "AED", null);
        var aliceAccount = Objects.requireNonNull(accountService.create(USER_OWNER_ID, aliceAccountRequest).block());
        var bobAccountRequest = new CreateAccountRequest(null, "USD", null);
        var bobAccount = Objects.requireNonNull(accountService.create(OTHER_OWNER_ID, bobAccountRequest).block());
        var alice2BobTransfer = new TransferRequest(
                aliceAccount.getAccountNumber(), bobAccount.getAccountNumber(), BigDecimal.valueOf(20), "alice2bob");
        var bob2AliceTransfer = new TransferRequest(
                bobAccount.getAccountNumber(), aliceAccount.getAccountNumber(), BigDecimal.valueOf(3), "bob2alice");

        var alice2bobFee = feeService.getExchangeFee(
                new CurrencyPair(aliceAccount.getCurrencyCode(), bobAccount.getCurrencyCode()),
                alice2BobTransfer.getAmount()
        ).block();
        var bob2AlicFee = feeService.getExchangeFee(
                new CurrencyPair(bobAccount.getCurrencyCode(), aliceAccount.getCurrencyCode()),
                bob2AliceTransfer.getAmount()
        ).block();
        var balanceByAccNumber = accountRepo.getAccounts().stream()
                .collect(Collectors.toMap(Account::getAccountNumber, Account::getBalance));

        ThrowingConsumer<Integer> round = times -> {
            var txnUUID1 = UUID.randomUUID();
            var txnUUID2 = UUID.randomUUID();

            Runnable lend = () -> testClient.put()
                    .uri("/transfer/" + txnUUID1)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                    .bodyValue(alice2BobTransfer)
                    .exchange()
                    .expectStatus().isOk().expectBody()
                    .jsonPath("accountId").isEqualTo(aliceAccount.getId())
                    .jsonPath("amount").isEqualTo(alice2BobTransfer.getAmount().negate());
            Runnable returnDebt = () -> testClient.put()
                    .uri("/transfer/" + txnUUID2)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, OTHER_USER_TOKEN)
                    .bodyValue(bob2AliceTransfer)
                    .exchange()
                    .expectStatus().isOk().expectBody()
                    .jsonPath("accountId").isEqualTo(bobAccount.getId())
                    .jsonPath("amount").isEqualTo(bob2AliceTransfer.getAmount().negate());
            lend.run();

            var txnGroup = txnGroupRepo.findByUUID(txnUUID1).block();
            assertThat(txnGroup).isNotNull()
                    .returns(alice2BobTransfer.getAmount(), TxnGroup::getAmount)
                    .returns("AED", TxnGroup::getCurrencyCode)
                    .returns(TxnType.TRANSFER, TxnGroup::getType)
                    .returns(alice2BobTransfer.getComment(), TxnGroup::getComment)
                    .returns(aliceAccount.getAccountNumber(), TxnGroup::getPayerAccountNumber)
                    .returns(bobAccount.getAccountNumber(), TxnGroup::getReceiverAccountNumber)
                    .returns(txnUUID1, TxnGroup::getTxnUUID)
                    .matches(txn -> txn.getCreatedAt() != null);

            lend.run();
            var alice2BobAmount = alice2BobTransfer.getAmount();
            var bob2AliceAmount = bob2AliceTransfer.getAmount();
            var aliceDelta = alice2BobAmount.multiply(BigDecimal.valueOf(times))
                    .subtract(bob2AliceAmount.subtract(bob2AlicFee).multiply(BigDecimal.valueOf(USD_2_AED)).multiply(BigDecimal.valueOf(times - 1)));
            var bobDelta = bob2AliceAmount.multiply(BigDecimal.valueOf(times - 1))
                    .subtract(alice2BobAmount.subtract(alice2bobFee).multiply(BigDecimal.valueOf(AED_2_USD)).multiply(BigDecimal.valueOf(times)));

            var newBalancesByNumber = accountRepo.getAccounts().stream()
                    .collect(Collectors.toMap(Account::getAccountNumber, Account::getBalance));
            assertThat(newBalancesByNumber.get(aliceAccount.getAccountNumber()))
                    .isEqualByComparingTo(balanceByAccNumber.get(aliceAccount.getAccountNumber()).subtract(aliceDelta));
            assertThat(newBalancesByNumber.get(bobAccount.getAccountNumber()))
                    .isEqualByComparingTo(balanceByAccNumber.get(bobAccount.getAccountNumber()).subtract(bobDelta));

            returnDebt.run();
            aliceDelta = alice2BobAmount.multiply(BigDecimal.valueOf(times))
                    .subtract(bob2AliceAmount.subtract(bob2AlicFee).multiply(BigDecimal.valueOf(USD_2_AED)).multiply(BigDecimal.valueOf(times)));
            bobDelta = bob2AliceAmount.multiply(BigDecimal.valueOf(times))
                    .subtract(alice2BobAmount.subtract(alice2bobFee).multiply(BigDecimal.valueOf(AED_2_USD)).multiply(BigDecimal.valueOf(times)));

            newBalancesByNumber = accountRepo.getAccounts().stream()
                    .collect(Collectors.toMap(Account::getAccountNumber, Account::getBalance));
            assertThat(newBalancesByNumber.get(aliceAccount.getAccountNumber()))
                    .isEqualByComparingTo(balanceByAccNumber.get(aliceAccount.getAccountNumber()).subtract(aliceDelta));
            assertThat(newBalancesByNumber.get(bobAccount.getAccountNumber()))
                    .isEqualByComparingTo(balanceByAccNumber.get(bobAccount.getAccountNumber()).subtract(bobDelta));
        };

        for (int i = 0; i < 3; i++) {
            round.accept(i + 1);
        }

        var newBalancesByNumber = accountRepo.getAccounts().stream()
                .collect(Collectors.toMap(Account::getAccountNumber, Account::getBalance));
        assertThat(newBalancesByNumber.get(aliceAccount.getAccountNumber()))
                .isLessThan(balanceByAccNumber.get(aliceAccount.getAccountNumber()));
        assertThat(newBalancesByNumber.get(bobAccount.getAccountNumber()))
                .isGreaterThan(balanceByAccNumber.get(bobAccount.getAccountNumber()));
    }

    @Test
    void sameCurrencyInterTransfer() {

        var aliceAccountRequest = new CreateAccountRequest(BigDecimal.valueOf(100), "AED", null);
        var aliceAccount = Objects.requireNonNull(accountService.create(USER_OWNER_ID, aliceAccountRequest).block());
        var sberAedNumber = getOrgAccount("AED", AccountType.CORRESPONDENT, SBERBANK).getAccountNumber();
        var alice2sberTransfer = new TransferRequest(
                aliceAccount.getAccountNumber(), sberAedNumber, BigDecimal.valueOf(60), "alice2sber");
        var txntUuid = UUID.randomUUID();

        var balanceByAccNumber = accountRepo.getAccounts().stream()
                .collect(Collectors.toMap(Account::getAccountNumber, Account::getBalance));

        testClient.put()
                .uri("/transfer/international/" + txntUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(alice2sberTransfer)
                .exchange()
                .expectStatus().isOk().expectBody()
                .jsonPath("accountId").isEqualTo(aliceAccount.getId())
                .jsonPath("amount").isEqualTo(alice2sberTransfer.getAmount().negate());

        assertThat(Objects.requireNonNull(accountRepo.findById(aliceAccount.getId()).block()).getLastTxnId()).isNotNull();

        var txnGroup = txnGroupRepo.findByUUID(txntUuid).block();
        assertThat(txnGroup).isNotNull()
                .returns(alice2sberTransfer.getAmount(), TxnGroup::getAmount)
                .returns("AED", TxnGroup::getCurrencyCode)
                .returns(TxnType.INTER_TRANSFER, TxnGroup::getType)
                .returns(alice2sberTransfer.getComment(), TxnGroup::getComment)
                .returns(aliceAccount.getAccountNumber(), TxnGroup::getPayerAccountNumber)
                .returns(sberAedNumber, TxnGroup::getReceiverAccountNumber)
                .returns(txntUuid, TxnGroup::getTxnUUID)
                .matches(txn -> txn.getCreatedAt() != null);

        var delta = alice2sberTransfer.getAmount();
        var fee = feeService.getInternationalFee("AED", delta).block();

        var aliceTxn = txnRepo.findByTxnGroupIdAndAccountIdAndSpendingType(
                txnGroup.getId(), aliceAccount.getId(), TxnSpendingType.TRANSFER
        ).block();
        var sberTxn = txnRepo.findById(Objects.requireNonNull(accountRepo.findByAccountNumber(sberAedNumber).block()).getLastTxnId());
        assertThat(aliceTxn).isNotNull()
                .returns(aliceAccount.getId(), Txn::getAccountId)
                .returns(delta.negate(), Txn::getAmount)
                .returns(TxnStatus.SUCCESS, Txn::getStatus)
                .matches(t -> t.getCreatedAt() != null && t.getDetails() != null);
        assertThat(sberTxn).isNotNull()
                .returns(delta.subtract(fee), Txn::getAmount)
                .returns(TxnStatus.SUCCESS, Txn::getStatus)
                .matches(txn -> !txn.getLinkingTxnId().equals(aliceTxn.getId()));

        var newBalancesByNumber = accountRepo.getAccounts().stream()
                .collect(Collectors.toMap(Account::getAccountNumber, Account::getBalance));
        assertThat(newBalancesByNumber.get(aliceAccount.getAccountNumber()))
                .isEqualTo(balanceByAccNumber.get(aliceAccount.getAccountNumber()).subtract(delta));
        assertThat(newBalancesByNumber.get(sberAedNumber))
                .isEqualTo(balanceByAccNumber.get(sberAedNumber).add(delta).subtract(fee));

        testClient.put()
                .uri("/transfer/international/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(alice2sberTransfer)
                .exchange()
                .expectStatus().isBadRequest();

        var lastOne = accountRepo.getAccounts().stream()
                .collect(Collectors.toMap(Account::getAccountNumber, Account::getBalance));
        assertThat(lastOne).allSatisfy((other, amount) ->
                assertThat(newBalancesByNumber.get(other)).isEqualByComparingTo(amount)
        );
    }

    @Test
    void otherCurrencyInternationalTransfer() {

        var aliceAccountRequest = new CreateAccountRequest(BigDecimal.valueOf(100), "AED", null);
        var aliceAccount = Objects.requireNonNull(accountService.create(USER_OWNER_ID, aliceAccountRequest).block());
        var sberUsdNumber = getOrgAccount("USD", AccountType.CORRESPONDENT, SBERBANK).getAccountNumber();
        var alice2sberTransfer = new TransferRequest(
                aliceAccount.getAccountNumber(), sberUsdNumber, BigDecimal.valueOf(60), "alice2sber");
        var txntUuid = UUID.randomUUID();

        var balanceByAccNumber = accountRepo.getAccounts().stream()
                .collect(Collectors.toMap(Account::getAccountNumber, Account::getBalance));

        testClient.put()
                .uri("/transfer/international/" + txntUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(alice2sberTransfer)
                .exchange()
                .expectStatus().isOk().expectBody()
                .jsonPath("accountId").isEqualTo(aliceAccount.getId())
                .jsonPath("amount").isEqualTo(alice2sberTransfer.getAmount().negate());

        assertThat(Objects.requireNonNull(accountRepo.findById(aliceAccount.getId()).block()).getLastTxnId()).isNotNull();

        var txnGroup = txnGroupRepo.findByUUID(txntUuid).block();
        assertThat(txnGroup).isNotNull()
                .returns(alice2sberTransfer.getAmount(), TxnGroup::getAmount)
                .returns("AED", TxnGroup::getCurrencyCode)
                .returns(TxnType.INTER_TRANSFER, TxnGroup::getType)
                .returns(alice2sberTransfer.getComment(), TxnGroup::getComment)
                .returns(aliceAccount.getAccountNumber(), TxnGroup::getPayerAccountNumber)
                .returns(sberUsdNumber, TxnGroup::getReceiverAccountNumber)
                .returns(txntUuid, TxnGroup::getTxnUUID)
                .matches(txn -> txn.getCreatedAt() != null);

        var delta = alice2sberTransfer.getAmount();
        var exchangeFee = feeService.getExchangeFee(new CurrencyPair("AED", "USD"), delta).block();
        var bought = delta.subtract(exchangeFee).multiply(BigDecimal.valueOf(AED_2_USD));
        var fee = feeService.getInternationalFee("USD", bought).block();
        var deposited = bought.subtract(fee);

        var aliceTxn = txnRepo.findByTxnGroupIdAndAccountIdAndSpendingType(
                txnGroup.getId(), aliceAccount.getId(), TxnSpendingType.TRANSFER
        ).block();
        var sberTxn = txnRepo.findById(Objects.requireNonNull(accountRepo.findByAccountNumber(sberUsdNumber).block()).getLastTxnId());
        assertThat(aliceTxn).isNotNull()
                .returns(aliceAccount.getId(), Txn::getAccountId)
                .returns(delta.negate(), Txn::getAmount)
                .returns(TxnStatus.SUCCESS, Txn::getStatus)
                .matches(t -> t.getCreatedAt() != null && t.getDetails() != null);
        assertThat(sberTxn).isNotNull()
                .returns(deposited, Txn::getAmount)
                .returns(TxnStatus.SUCCESS, Txn::getStatus)
                .matches(txn -> !txn.getLinkingTxnId().equals(aliceTxn.getId()));

        var newBalancesByNumber = accountRepo.getAccounts().stream()
                .collect(Collectors.toMap(Account::getAccountNumber, Account::getBalance));
        assertThat(newBalancesByNumber.get(aliceAccount.getAccountNumber()))
                .isEqualTo(balanceByAccNumber.get(aliceAccount.getAccountNumber()).subtract(delta));
        assertThat(newBalancesByNumber.get(sberUsdNumber))
                .isEqualTo(balanceByAccNumber.get(sberUsdNumber).add(deposited));

        testClient.put()
                .uri("/transfer/international/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                .bodyValue(alice2sberTransfer)
                .exchange()
                .expectStatus().isBadRequest();

        var lastOne = accountRepo.getAccounts().stream()
                .collect(Collectors.toMap(Account::getAccountNumber, Account::getBalance));
        assertThat(lastOne).allSatisfy((other, amount) ->
                assertThat(newBalancesByNumber.get(other)).isEqualByComparingTo(amount)
        );
    }

    private Account getOrgAccount(String currencyCode, AccountType type, String ownerId) {
        return accountRepo.getAccounts()
                .stream()
                .filter(acc -> acc.getCurrencyCode().equals(currencyCode) && acc.getType() == type && acc.getOwnerId().equals(ownerId))
                .findFirst()
                .orElse(null);
    }
}

package io.shmaks.banking.service;

import io.shmaks.banking.ext.CurrencyPair;
import io.shmaks.banking.ext.CurrencyService;
import io.shmaks.banking.ext.FeeService;
import io.shmaks.banking.model.AccountType;
import io.shmaks.banking.model.TxnGroup;
import io.shmaks.banking.model.TxnSpendingType;
import io.shmaks.banking.repo.AccountRepo;
import io.shmaks.banking.repo.TxnGroupRepo;
import io.shmaks.banking.repo.TxnRepo;
import io.shmaks.banking.service.dto.DepositRequest;
import io.shmaks.banking.service.dto.TxnResult;
import io.shmaks.banking.service.dto.WithdrawalRequest;
import io.shmaks.banking.service.processors.DepositProcessor;
import io.shmaks.banking.service.processors.WithdrawalProcessor;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TransferService {

    private final TxnGroupRepo txnGroupRepo;
    private final TxnRepo txnRepo;
    private final AccountRepo accountRepo;
    private final CurrencyService currencyService;
    private final FeeService feeService;

    private final DepositProcessor depositProcessor;
    private final WithdrawalProcessor withdrawalProcessor;

    public TransferService(
            TxnGroupRepo txnGroupRepo,
            TxnRepo txnRepo,
            AccountRepo accountRepo,
            CurrencyService currencyService,
            FeeService feeService) {
        this.txnGroupRepo = txnGroupRepo;
        this.txnRepo = txnRepo;
        this.accountRepo = accountRepo;
        this.currencyService = currencyService;
        this.feeService = feeService;

        this.depositProcessor = new DepositProcessor(txnGroupRepo, txnRepo, accountRepo);
        this.withdrawalProcessor = new WithdrawalProcessor(txnGroupRepo, txnRepo, accountRepo);
    }

    @Transactional
    public Mono<TxnResult> deposit(DepositRequest request, UUID txnUUID) {
        var existing = txnGroupRepo.findByUUID(txnUUID);

        return existing.switchIfEmpty(doDeposit(request, txnUUID))
                .flatMap(txnGroup -> fetchExisting(txnGroup, request.getAccountNumber()));
    }

    @Transactional
    public Mono<TxnResult> withdraw(WithdrawalRequest request, UUID txnUUID) {
        var existing = txnGroupRepo.findByUUID(txnUUID);

        return existing.switchIfEmpty(doWithdraw(request, txnUUID))
                .flatMap(txnGroup -> fetchExisting(txnGroup, request.getAccountNumber()));
    }

    private Mono<TxnGroup> doDeposit(DepositRequest request, UUID txnUuid) {
        var accountNumber = request.getAccountNumber();

        var existingAccount = accountRepo
                .findByAccountNumber(accountNumber)
                .filter(account -> account.getType() == AccountType.USER)
                .switchIfEmpty(Mono.error(new BusinessLogicError("Unknown user account " + accountNumber)));

        return existingAccount.flatMap(account -> {
            if (account.getCurrencyCode().equals(request.getCurrencyCode())) {
                return depositProcessor.makeSimpleDeposit(request, txnUuid);
            } else {
                var currencyPair = new CurrencyPair(request.getCurrencyCode(), account.getCurrencyCode());
                var rate = currencyService.getRates(List.of(currencyPair))
                        .handle((Map<CurrencyPair, Double> rates, SynchronousSink<Double> sink) -> {
                            if (rates.size() == 1) {
                                sink.next(rates.values().iterator().next());
                            }
                        })
                        .switchIfEmpty(Mono.error(new BusinessLogicError("Unsupported currency pair " + currencyPair)));
                var fee = feeService.getExchangeFee(currencyPair, request.getAmount());
                return Mono.zip(rate, fee).flatMap(tuple -> depositProcessor.makeCrossCurrencyDeposit(
                        request, account.getCurrencyCode(), txnUuid, BigDecimal.valueOf(tuple.getT1()), tuple.getT2()
                ));
            }
        });
    }

    private Mono<TxnGroup> doWithdraw(WithdrawalRequest request, UUID txnUuid) {
        var accountNumber = request.getAccountNumber();

        var existingAccount = accountRepo
                .findByAccountNumber(accountNumber)
                .filter(account -> account.getType() == AccountType.USER)
                .switchIfEmpty(Mono.error(new BusinessLogicError("Unknown user account " + accountNumber)));

        return existingAccount.flatMap(account -> {
            if (account.getCurrencyCode().equals(request.getCurrencyCode())) {
                return withdrawalProcessor.makeSimpleWithdrawal(request, txnUuid);
            } else {
                var currencyPair = new CurrencyPair(account.getCurrencyCode(), request.getCurrencyCode());
                var rate = currencyService.getRates(List.of(currencyPair))
                        .handle((Map<CurrencyPair, Double> rates, SynchronousSink<Double> sink) -> {
                            if (rates.size() == 1) {
                                sink.next(rates.values().iterator().next());
                            }
                        })
                        .switchIfEmpty(Mono.error(new BusinessLogicError("Unsupported currency pair " + currencyPair)));
                var fee = feeService.getExchangeFee(currencyPair, request.getAmount());
                return Mono.zip(rate, fee).flatMap(tuple -> withdrawalProcessor.makeCrossCurrencyWithdrawal(
                        request, account.getCurrencyCode(), txnUuid, BigDecimal.valueOf(tuple.getT1()), tuple.getT2()
                ));
            }
        });
    }

    private Mono<TxnResult> fetchExisting(TxnGroup txnGroup, String accountNumber) {
        return accountRepo.findByAccountNumber(accountNumber).flatMap(account ->
                txnRepo.findByTxnGroupIdAndAccountIdAndSpendingType(
                        txnGroup.getId(), account.getId(), TxnSpendingType.TRANSFER
                ).map(txn -> new TxnResult(txn, account))
        );
    }
}

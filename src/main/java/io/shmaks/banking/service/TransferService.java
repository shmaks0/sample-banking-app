package io.shmaks.banking.service;

import io.shmaks.banking.ext.CurrencyPair;
import io.shmaks.banking.ext.CurrencyService;
import io.shmaks.banking.ext.FeeService;
import io.shmaks.banking.model.Account;
import io.shmaks.banking.model.AccountType;
import io.shmaks.banking.model.TxnGroup;
import io.shmaks.banking.model.TxnSpendingType;
import io.shmaks.banking.repo.AccountRepo;
import io.shmaks.banking.repo.TxnGroupRepo;
import io.shmaks.banking.repo.TxnRepo;
import io.shmaks.banking.service.dto.DepositRequest;
import io.shmaks.banking.service.dto.TransferRequest;
import io.shmaks.banking.service.dto.TxnResult;
import io.shmaks.banking.service.dto.WithdrawalRequest;
import io.shmaks.banking.service.processors.DepositProcessor;
import io.shmaks.banking.service.processors.InternationalTransferProcessor;
import io.shmaks.banking.service.processors.TransferProcessor;
import io.shmaks.banking.service.processors.WithdrawalProcessor;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class TransferService {

    private final TxnGroupRepo txnGroupRepo;
    private final TxnRepo txnRepo;
    private final AccountRepo accountRepo;
    private final CurrencyService currencyService;
    private final FeeService feeService;

    private final DepositProcessor depositProcessor;
    private final WithdrawalProcessor withdrawalProcessor;
    private final TransferProcessor transferProcessor;
    private final InternationalTransferProcessor interTransferProcessor;

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
        this.transferProcessor = new TransferProcessor(txnGroupRepo, txnRepo, accountRepo);
        this.interTransferProcessor = new InternationalTransferProcessor(txnGroupRepo, txnRepo, accountRepo);
    }

    @Transactional
    public Mono<TxnResult> deposit(DepositRequest request, String ownerId, UUID txnUUID) {
        var accountNumber = request.getAccountNumber();

        var userAccount = accountRepo
                .findByAccountNumber(accountNumber)
                .filter(account -> account.getType() == AccountType.USER && account.getOwnerId().equals(ownerId))
                .switchIfEmpty(Mono.error(new BusinessLogicError("Unknown user account " + accountNumber)))
                .cache();

        var existing = txnGroupRepo.findByUUID(txnUUID);

        return existing
                .flatMap(group -> {
                    if (!Objects.equals(group.getReceiverAccountNumber(), accountNumber)) {
                        return Mono.error(new BusinessLogicError("Unknown user account " + accountNumber));
                    }
                    return Mono.just(group);
                })
                .switchIfEmpty(doDeposit(request, userAccount, txnUUID))
                .flatMap(txnGroup -> fetchExisting(txnGroup, userAccount));
    }

    @Transactional
    public Mono<TxnResult> withdraw(WithdrawalRequest request, String ownerId, UUID txnUUID) {
        var accountNumber = request.getAccountNumber();

        var userAccount = accountRepo
                .findByAccountNumber(accountNumber)
                .filter(account -> account.getType() == AccountType.USER && account.getOwnerId().equals(ownerId))
                .switchIfEmpty(Mono.error(new BusinessLogicError("Unknown user account " + accountNumber)))
                .cache();

        var existing = txnGroupRepo.findByUUID(txnUUID);

        return existing
                .flatMap(group -> {
                    if (!Objects.equals(group.getPayerAccountNumber(), accountNumber)) {
                        return Mono.error(new BusinessLogicError("Unknown user account " + accountNumber));
                    }
                    return Mono.just(group);
                })
                .switchIfEmpty(doWithdraw(request, userAccount, txnUUID))
                .flatMap(txnGroup -> fetchExisting(txnGroup, userAccount));
    }

    @Transactional
    public Mono<TxnResult> transfer(TransferRequest request, String ownerId, UUID txnUUID) {
        var payerAccNum = request.getPayerAccountNumber();
        var receiverAccNum = request.getReceiverAccountNumber();

        var payerAccount = accountRepo
                .findByAccountNumber(payerAccNum)
                .filter(account -> account.getType() == AccountType.USER && account.getOwnerId().equals(ownerId))
                .switchIfEmpty(Mono.error(new BusinessLogicError("Unknown user account " + payerAccNum)));

        var receiverAccount = accountRepo
                .findByAccountNumber(receiverAccNum)
                .filter(account -> account.getType() == AccountType.USER)
                .switchIfEmpty(Mono.error(new BusinessLogicError("Unknown user account " + receiverAccNum)));

        var existing = txnGroupRepo.findByUUID(txnUUID);

        return existing
                .flatMap(group -> {
                    if (!Objects.equals(group.getPayerAccountNumber(), payerAccNum)) {
                        return Mono.error(new BusinessLogicError("Unknown user account " + payerAccNum));
                    }
                    return Mono.just(group);
                })
                .switchIfEmpty(doTransfer(request, payerAccount, receiverAccount, txnUUID))
                .flatMap(txnGroup -> fetchExisting(txnGroup, payerAccount));
    }

    @Transactional
    public Mono<TxnResult> internationalTransfer(TransferRequest request, String ownerId, UUID txnUUID) {
        var payerAccNum = request.getPayerAccountNumber();
        var receiverAccNum = request.getReceiverAccountNumber();

        var payerAccount = accountRepo
                .findByAccountNumber(payerAccNum)
                .filter(account -> account.getType() == AccountType.USER && account.getOwnerId().equals(ownerId))
                .switchIfEmpty(Mono.error(new BusinessLogicError("Unknown user account " + payerAccNum)));

        var receiverAccount = accountRepo
                .findByAccountNumber(receiverAccNum)
                .filter(account -> account.getType() == AccountType.CORRESPONDENT)
                .switchIfEmpty(Mono.error(new BusinessLogicError("Unknown correspondent account " + receiverAccNum)));

        var existing = txnGroupRepo.findByUUID(txnUUID);

        return existing
                .flatMap(group -> {
                    if (!Objects.equals(group.getPayerAccountNumber(), payerAccNum)) {
                        return Mono.error(new BusinessLogicError("Unknown user account " + payerAccNum));
                    }
                    return Mono.just(group);
                })
                .switchIfEmpty(doInterTransfer(request, payerAccount, receiverAccount, txnUUID))
                .flatMap(txnGroup -> fetchExisting(txnGroup, payerAccount));
    }

    private Mono<TxnGroup> doDeposit(DepositRequest request, Mono<Account> userAccount, UUID txnUuid) {
        return userAccount.flatMap(account -> {
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

    private Mono<TxnGroup> doWithdraw(WithdrawalRequest request, Mono<Account> userAccount, UUID txnUuid) {
        return userAccount.flatMap(account -> {
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
                var rateAndFee = rate.zipWhen(r ->
                        feeService.getExchangeFee(
                                currencyPair, request.getAmount().divide(BigDecimal.valueOf(r), RoundingMode.HALF_UP)
                        )
                );
                return rateAndFee.flatMap(tuple -> withdrawalProcessor.makeCrossCurrencyWithdrawal(
                        request, account.getCurrencyCode(), txnUuid, BigDecimal.valueOf(tuple.getT1()), tuple.getT2()
                ));
            }
        });
    }

    private Mono<TxnGroup> doTransfer(
            TransferRequest request, Mono<Account> payerAccount, Mono<Account> receiverAccount, UUID txnUuid) {
        return Mono.zip(payerAccount, receiverAccount).flatMap(accounts -> {
            var payerCurrency = accounts.getT1().getCurrencyCode();
            var receiverCurrency = accounts.getT2().getCurrencyCode();
            if (payerCurrency.equals(receiverCurrency)) {
                return transferProcessor.makeSimpleTransfer(request, txnUuid);
            } else {
                var currencyPair = new CurrencyPair(payerCurrency, receiverCurrency);
                //noinspection DuplicatedCode
                var rate = currencyService.getRates(List.of(currencyPair))
                        .handle((Map<CurrencyPair, Double> rates, SynchronousSink<Double> sink) -> {
                            if (rates.size() == 1) {
                                sink.next(rates.values().iterator().next());
                            }
                        })
                        .switchIfEmpty(Mono.error(new BusinessLogicError("Unsupported currency pair " + currencyPair)));
                var fee = feeService.getExchangeFee(currencyPair, request.getAmount());
                return Mono.zip(rate, fee).flatMap(tuple -> transferProcessor.makeCrossCurrencyTransfer(
                        request, payerCurrency, receiverCurrency, txnUuid, BigDecimal.valueOf(tuple.getT1()), tuple.getT2()
                ));
            }
        });
    }

    private Mono<TxnGroup> doInterTransfer(
            TransferRequest request, Mono<Account> payerAccount, Mono<Account> receiverAccount, UUID txnUuid) {
        return Mono.zip(payerAccount, receiverAccount).flatMap(accounts -> {
            var payerCurrency = accounts.getT1().getCurrencyCode();
            var receiverCurrency = accounts.getT2().getCurrencyCode();

            if (payerCurrency.equals(receiverCurrency)) {
                return feeService.getInternationalFee(payerCurrency, request.getAmount()).flatMap(fee ->
                        interTransferProcessor.makeSimpleTransfer(request, payerCurrency, txnUuid, fee)
                );
            } else {
                var currencyPair = new CurrencyPair(payerCurrency, receiverCurrency);
                //noinspection DuplicatedCode
                var rate = currencyService.getRates(List.of(currencyPair))
                        .handle((Map<CurrencyPair, Double> rates, SynchronousSink<Double> sink) -> {
                            if (rates.size() == 1) {
                                sink.next(rates.values().iterator().next());
                            }
                        })
                        .switchIfEmpty(Mono.error(new BusinessLogicError("Unsupported currency pair " + currencyPair)));
                var fee = feeService.getExchangeFee(currencyPair, request.getAmount());
                return Mono.zip(rate, fee).flatMap(tuple -> {
                    var exchangeFee = tuple.getT2();
                    var rateAmount = BigDecimal.valueOf(tuple.getT1());
                    var boughtAmount = request.getAmount().subtract(exchangeFee).multiply(rateAmount);

                    return feeService.getInternationalFee(receiverCurrency, boughtAmount).flatMap(interFee ->
                            interTransferProcessor.makeCrossCurrencyTransfer(
                                    request, payerCurrency, receiverCurrency, txnUuid, rateAmount, exchangeFee, interFee
                            )
                    );
                });
            }
        });
    }

    private Mono<TxnResult> fetchExisting(TxnGroup txnGroup, Mono<Account> userAccount) {
        return userAccount.flatMap(account ->
                txnRepo.findByTxnGroupIdAndAccountIdAndSpendingType(
                        txnGroup.getId(), account.getId(), TxnSpendingType.TRANSFER
                ).map(txn -> new TxnResult(txn, account))
        );
    }
}

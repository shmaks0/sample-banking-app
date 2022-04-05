package io.shmaks.banking.service.processors;

import io.shmaks.banking.model.*;
import io.shmaks.banking.repo.AccountRepo;
import io.shmaks.banking.repo.TxnGroupRepo;
import io.shmaks.banking.repo.TxnRepo;
import io.shmaks.banking.service.RetryLaterException;
import io.shmaks.banking.service.dto.DepositRequest;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public class DepositProcessor extends BaseProcessor {

    public DepositProcessor(TxnGroupRepo txnGroupRepo, TxnRepo txnRepo, AccountRepo accountRepo) {
        super(txnGroupRepo, txnRepo, accountRepo);
    }

    public Mono<TxnGroup> makeSimpleDeposit(DepositRequest request, UUID txnUUID) {
        return getOrgAccount(request.getCurrencyCode(), AccountType.BASE).flatMap(baseAcc -> {
            var accountNumbers = Set.of(baseAcc.getAccountNumber(), request.getAccountNumber());
            return accountRepo.selectForUpdate(accountNumbers)
                    .flatMap(handle -> {
                        try (handle) {
                            var customerAccount = handle.getAccounts().get(request.getAccountNumber());
                            var orgAccount = handle.getAccounts().get(baseAcc.getAccountNumber());
                            return createGroup(request, txnUUID, TxnType.DEPOSIT)
                                    .flatMap(group -> performTransfer(
                                            group, TxnSpendingType.TRANSFER,
                                            customerAccount, orgAccount,
                                            request.getAmount(), request.getAmount().negate(),
                                            "Deposit: " + request.getComment(),
                                            "Deposit to " + request.getAccountNumber() + ": " + txnUUID
                                    ).thenReturn(group));
                        }
                    })
                    .switchIfEmpty(Mono.error(new RetryLaterException()));
        });
    }

    public Mono<TxnGroup> makeCrossCurrencyDeposit(
            DepositRequest request, String userCurrency, UUID txnUUID, BigDecimal rate, BigDecimal fee
    ) {
        return Mono.zip(
                getOrgAccount(request.getCurrencyCode(), AccountType.BASE),
                getOrgAccount(request.getCurrencyCode(), AccountType.FEE),
                getOrgAccount(userCurrency, AccountType.BASE)
        ).flatMap(orgAccounts -> {
            var accountNumbers = Set.of(
                    orgAccounts.getT1().getAccountNumber(),
                    orgAccounts.getT2().getAccountNumber(),
                    orgAccounts.getT3().getAccountNumber(),
                    request.getAccountNumber()
            );

            return accountRepo.selectForUpdate(accountNumbers)
                    .flatMap(handle -> {
                        try (handle) {
                            return performMultiCurrencyDeposit(request, txnUUID, rate, fee, orgAccounts, handle);
                        }
                    })
                    .switchIfEmpty(Mono.error(new RetryLaterException()));
        });
    }

    private Mono<TxnGroup> performMultiCurrencyDeposit(
            DepositRequest request, UUID txnUUID, BigDecimal rate, BigDecimal fee,
            Tuple3<Account, Account, Account> orgAccounts, AccountRepo.LockHandle handle) {
        var baseForRequest = handle.getAccounts().get(orgAccounts.getT1().getAccountNumber());
        var feeForRequest = handle.getAccounts().get(orgAccounts.getT2().getAccountNumber());
        var baseForUser = handle.getAccounts().get(orgAccounts.getT3().getAccountNumber());
        var customerAccount = handle.getAccounts().get(request.getAccountNumber());

        var exchangeFeeComment = "Exchange fee for deposit#" + txnUUID + " to " + request.getAccountNumber();
        var exchangeComment = "Currency Exchange for deposit#" + txnUUID + " to " + request.getAccountNumber();
        var userComment = "Deposit: " + request.getComment();
        var baseCreditComment = "Deposit to " + request.getAccountNumber() + ": " + txnUUID;

        var depositAmount = request.getAmount()
                .subtract(fee)
                .multiply(rate);

        return createGroup(request, txnUUID, TxnType.DEPOSIT)
                .flatMap(group ->
                        performTransfer(
                                group, TxnSpendingType.EXCHANGE_FEE,
                                feeForRequest, baseForRequest,
                                fee, fee.negate(),
                                exchangeFeeComment, exchangeFeeComment
                        )
                                .then(performTransfer(
                                        group, TxnSpendingType.EXCHANGE,
                                        baseForUser, baseForRequest,
                                        depositAmount, request.getAmount().subtract(fee).negate(),
                                        exchangeComment, exchangeComment
                                ))
                                .then(performTransfer(
                                        group, TxnSpendingType.TRANSFER,
                                        customerAccount, baseForUser,
                                        depositAmount, depositAmount.negate(),
                                        userComment, baseCreditComment
                                ))
                                .thenReturn(group)
                );
    }

}

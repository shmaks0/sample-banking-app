package io.shmaks.banking.service.processors;

import io.shmaks.banking.model.*;
import io.shmaks.banking.repo.AccountRepo;
import io.shmaks.banking.repo.TxnGroupRepo;
import io.shmaks.banking.repo.TxnRepo;
import io.shmaks.banking.service.BusinessLogicError;
import io.shmaks.banking.service.RetryLaterException;
import io.shmaks.banking.service.dto.WithdrawalRequest;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public class WithdrawalProcessor extends BaseProcessor {

    public WithdrawalProcessor(TxnGroupRepo txnGroupRepo, TxnRepo txnRepo, AccountRepo accountRepo) {
        super(txnGroupRepo, txnRepo, accountRepo);
    }

    public Mono<TxnGroup> makeSimpleWithdrawal(WithdrawalRequest request, UUID txnUUID) {
        return getOrgAccount(request.getCurrencyCode(), AccountType.BASE).flatMap(baseAcc -> {
            var accountNumbers = Set.of(baseAcc.getAccountNumber(), request.getAccountNumber());
            return accountRepo.selectForUpdate(accountNumbers)
                    .flatMap(handle -> {
                        try (handle) {
                            var customerAccount = handle.getAccounts().get(request.getAccountNumber());

                            if (customerAccount.getBalance().compareTo(request.getAmount()) < 0) {
                                return Mono.error(new BusinessLogicError("Insufficient funds")); // todo: return failed transaction
                            }

                            var orgAccount = handle.getAccounts().get(baseAcc.getAccountNumber());
                            return createGroup(request, txnUUID)
                                    .flatMap(group -> performTransfer(
                                            group, TxnSpendingType.TRANSFER,
                                            orgAccount, customerAccount,
                                            request.getAmount(), request.getAmount().negate(),
                                            "Withdrawal: " + request.getComment(),
                                            "Withdrawal from " + request.getAccountNumber() + ": " + txnUUID
                                    ).thenReturn(group));
                        }
                    })
                    .switchIfEmpty(Mono.error(new RetryLaterException()));
        });
    }

    public Mono<TxnGroup> makeCrossCurrencyWithdrawal(
            WithdrawalRequest request, String userCurrency, UUID txnUUID, BigDecimal rate, BigDecimal fee
    ) {
        return Mono.zip(
                getOrgAccount(request.getCurrencyCode(), AccountType.BASE),
                getOrgAccount(userCurrency, AccountType.FEE),
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
                            return performMultiCurrencyWithdrawal(request, txnUUID, rate, fee, orgAccounts, handle);
                        }
                    })
                    .switchIfEmpty(Mono.error(new RetryLaterException()));
        });
    }

    private Mono<TxnGroup> performMultiCurrencyWithdrawal(
            WithdrawalRequest request, UUID txnUUID, BigDecimal rate, BigDecimal fee,
            Tuple3<Account, Account, Account> orgAccounts, AccountRepo.LockHandle handle) {
        var baseForRequest = handle.getAccounts().get(orgAccounts.getT1().getAccountNumber());
        var feeForUser = handle.getAccounts().get(orgAccounts.getT2().getAccountNumber());
        var baseForUser = handle.getAccounts().get(orgAccounts.getT3().getAccountNumber());
        var customerAccount = handle.getAccounts().get(request.getAccountNumber());

        var withdrawnAmount = request.getAmount().multiply(rate).add(fee);

        if (customerAccount.getBalance().compareTo(withdrawnAmount) < 0) {
            return Mono.error(new BusinessLogicError("Insufficient funds")); // todo: return failed transaction
        }

        var exchangeFeeComment = "Exchange fee for withdrawal#" + txnUUID + " to " + request.getAccountNumber();
        var exchangeComment = "Currency Exchange for withdrawal#" + txnUUID + " to " + request.getAccountNumber();
        var userComment = "Withdrawal: " + request.getComment();
        var baseDebitComment = "Withdrawal from " + request.getAccountNumber() + ": " + txnUUID;

        return createGroup(request, txnUUID)
                .flatMap(group ->
                        performTransfer(
                                group, TxnSpendingType.TRANSFER,
                                baseForUser, customerAccount,
                                withdrawnAmount, withdrawnAmount.negate(),
                                baseDebitComment, userComment
                        )
                                .then(performTransfer(
                                        group, TxnSpendingType.EXCHANGE,
                                        feeForUser, baseForUser,
                                        fee, fee.negate(),
                                        exchangeFeeComment, exchangeFeeComment
                                ))
                                .then(performTransfer(
                                        group, TxnSpendingType.EXCHANGE,
                                        baseForRequest, baseForUser,
                                        request.getAmount(), request.getAmount().multiply(rate).negate(),
                                        exchangeComment, exchangeComment
                                ))
                                .thenReturn(group)
                );
    }

}

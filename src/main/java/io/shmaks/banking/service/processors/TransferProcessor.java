package io.shmaks.banking.service.processors;

import io.shmaks.banking.model.*;
import io.shmaks.banking.repo.AccountRepo;
import io.shmaks.banking.repo.TxnGroupRepo;
import io.shmaks.banking.repo.TxnRepo;
import io.shmaks.banking.service.BusinessLogicError;
import io.shmaks.banking.service.RetryLaterException;
import io.shmaks.banking.service.dto.TransferRequest;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public class TransferProcessor extends BaseProcessor {

    public TransferProcessor(TxnGroupRepo txnGroupRepo, TxnRepo txnRepo, AccountRepo accountRepo) {
        super(txnGroupRepo, txnRepo, accountRepo);
    }

    public Mono<TxnGroup> makeSimpleTransfer(TransferRequest request, UUID txnUUID) {
        var accountNumbers = Set.of(request.getPayerAccountNumber(), request.getReceiverAccountNumber());
        return accountRepo.selectForUpdate(accountNumbers)
                .flatMap(handle -> {
                    try (handle) {
                        var payerAccount = handle.getAccounts().get(request.getPayerAccountNumber());
                        var receiverAccount = handle.getAccounts().get(request.getReceiverAccountNumber());

                        if (payerAccount.getBalance().compareTo(request.getAmount()) < 0) {
                            return Mono.error(new BusinessLogicError("Insufficient funds")); // todo: return failed transaction
                        }
                        return createGroup(request, txnUUID, TxnType.TRANSFER, payerAccount.getCurrencyCode())
                                .flatMap(group -> performTransfer(
                                        group, TxnSpendingType.TRANSFER,
                                        receiverAccount, payerAccount,
                                        request.getAmount(), request.getAmount().negate(),
                                        "Transfer from " + request.getPayerAccountNumber() + ": " + request.getComment(),
                                        "Transfer to " + request.getReceiverAccountNumber() + ": " + request.getComment()
                                ).thenReturn(group));
                    }
                })
                .switchIfEmpty(Mono.error(new RetryLaterException()));
    }

    public Mono<TxnGroup> makeCrossCurrencyTransfer(
            TransferRequest request, String payerCurrency, String receiverCurrency, UUID txnUUID, BigDecimal rate, BigDecimal fee
    ) {
        return Mono.zip(
                getOrgAccount(payerCurrency, AccountType.BASE),
                getOrgAccount(payerCurrency, AccountType.FEE),
                getOrgAccount(receiverCurrency, AccountType.BASE)
        ).flatMap(orgAccounts -> {
            var accountNumbers = Set.of(
                    orgAccounts.getT1().getAccountNumber(),
                    orgAccounts.getT2().getAccountNumber(),
                    orgAccounts.getT3().getAccountNumber(),
                    request.getPayerAccountNumber(),
                    request.getReceiverAccountNumber()
            );

            return accountRepo.selectForUpdate(accountNumbers)
                    .flatMap(handle -> {
                        try (handle) {
                            return performCrossCurrencyTransfer(request, txnUUID, rate, fee, orgAccounts, handle);
                        }
                    })
                    .switchIfEmpty(Mono.error(new RetryLaterException()));
        });
    }

    private Mono<TxnGroup> performCrossCurrencyTransfer(
            TransferRequest request, UUID txnUUID, BigDecimal rate, BigDecimal fee,
            Tuple3<Account, Account, Account> orgAccounts, AccountRepo.LockHandle handle) {
        var baseForPayer = handle.getAccounts().get(orgAccounts.getT1().getAccountNumber());
        var feeForPayer = handle.getAccounts().get(orgAccounts.getT2().getAccountNumber());
        var baseForReceiver = handle.getAccounts().get(orgAccounts.getT3().getAccountNumber());
        var payerAccount = handle.getAccounts().get(request.getPayerAccountNumber());
        var receiverAccount = handle.getAccounts().get(request.getReceiverAccountNumber());

        var withdrawnAmount = request.getAmount();
        var depositAmount = withdrawnAmount.subtract(fee).multiply(rate);

        if (payerAccount.getBalance().compareTo(withdrawnAmount) < 0) {
            return Mono.error(new BusinessLogicError("Insufficient funds")); // todo: return failed transaction
        }

        var exchangeFeeComment = "Exchange fee for transfer#" + txnUUID + " from " + request.getPayerAccountNumber() +
                " to " + request.getReceiverAccountNumber();
        var exchangeComment = "Currency Exchange for transfer#" + txnUUID + " from " + request.getPayerAccountNumber() +
                " to " + request.getReceiverAccountNumber();
        var payerComment = "Transfer to " + request.getReceiverAccountNumber() + ": " + request.getComment();
        var receiverComment = "Transfer from " + request.getPayerAccountNumber() + ": " + request.getComment();
        var baseComment = "Transfer#" + txnUUID + " from " + request.getPayerAccountNumber() +
                " to " + request.getReceiverAccountNumber();

        return createGroup(request, txnUUID, TxnType.TRANSFER, payerAccount.getCurrencyCode())
                .flatMap(group ->
                        performTransfer(
                                group, TxnSpendingType.TRANSFER,
                                baseForPayer, payerAccount,
                                withdrawnAmount, withdrawnAmount.negate(),
                                baseComment, payerComment
                        )
                                .then(performTransfer(
                                        group, TxnSpendingType.EXCHANGE_FEE,
                                        feeForPayer, baseForPayer,
                                        fee, fee.negate(),
                                        exchangeFeeComment, exchangeFeeComment
                                ))
                                .then(performTransfer(
                                        group, TxnSpendingType.EXCHANGE,
                                        baseForReceiver, baseForPayer,
                                        depositAmount, withdrawnAmount.subtract(fee).negate(),
                                        exchangeComment, exchangeComment
                                ))
                                .then(performTransfer(
                                        group, TxnSpendingType.TRANSFER,
                                        receiverAccount, baseForReceiver,
                                        depositAmount, depositAmount.negate(),
                                        receiverComment, baseComment
                                ))
                                .thenReturn(group)
                );
    }

}

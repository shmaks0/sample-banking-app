package io.shmaks.banking.service.processors;

import io.shmaks.banking.model.*;
import io.shmaks.banking.repo.AccountRepo;
import io.shmaks.banking.repo.TxnGroupRepo;
import io.shmaks.banking.repo.TxnRepo;
import io.shmaks.banking.service.BusinessLogicError;
import io.shmaks.banking.service.RetryLaterException;
import io.shmaks.banking.service.dto.TransferRequest;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple4;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public class InternationalTransferProcessor extends BaseProcessor {

    public InternationalTransferProcessor(TxnGroupRepo txnGroupRepo, TxnRepo txnRepo, AccountRepo accountRepo) {
        super(txnGroupRepo, txnRepo, accountRepo);
    }

    public Mono<TxnGroup> makeSimpleTransfer(TransferRequest request, String currency, UUID txnUUID, BigDecimal fee) {
        return Mono.zip(
                getOrgAccount(currency, AccountType.BASE),
                getOrgAccount(currency, AccountType.FEE)
        ).flatMap(orgAccounts -> {
            var accountNumbers = Set.of(
                    orgAccounts.getT1().getAccountNumber(),
                    orgAccounts.getT2().getAccountNumber(),
                    request.getPayerAccountNumber(),
                    request.getReceiverAccountNumber()
            );

            return accountRepo.selectForUpdate(accountNumbers)
                    .flatMap(handle -> {
                        try (handle) {
                            var payerAccount = handle.getAccounts().get(request.getPayerAccountNumber());
                            var baseAccount = handle.getAccounts().get(orgAccounts.getT1().getAccountNumber());
                            var feeAccount = handle.getAccounts().get(orgAccounts.getT2().getAccountNumber());
                            var receiverAccount = handle.getAccounts().get(request.getReceiverAccountNumber());

                            if (payerAccount.getBalance().compareTo(request.getAmount()) < 0) {
                                return Mono.error(new BusinessLogicError("Insufficient funds")); // todo: return failed transaction
                            }

                            var feeComment = "InterTransfer fee for transfer#" + txnUUID + " from " + request.getPayerAccountNumber() +
                                    " to " + request.getReceiverAccountNumber();
                            var payerComment = "International transfer to " + request.getReceiverAccountNumber() + ": " + request.getComment();
                            var baseComment = "International Transfer#" + txnUUID + " from " + request.getPayerAccountNumber() +
                                    " to " + request.getReceiverAccountNumber();

                            return createGroup(request, txnUUID, TxnType.INTER_TRANSFER, currency)
                                    .flatMap(group -> performTransfer(
                                            group, TxnSpendingType.TRANSFER,
                                            baseAccount, payerAccount,
                                            request.getAmount(), request.getAmount().negate(),
                                            baseComment, payerComment
                                    ).then(performTransfer(
                                                    group, TxnSpendingType.FEE,
                                                    feeAccount, baseAccount,
                                                    fee, fee.negate(),
                                                    feeComment, feeComment
                                            ))
                                            .then(performTransfer(
                                                    group, TxnSpendingType.TRANSFER,
                                                    receiverAccount, baseAccount,
                                                    request.getAmount().subtract(fee), request.getAmount().subtract(fee).negate(),
                                                    baseComment, baseComment
                                            )).thenReturn(group));
                        }
                    })
                    .switchIfEmpty(Mono.error(new RetryLaterException()));
        });


    }

    public Mono<TxnGroup> makeCrossCurrencyTransfer(
            TransferRequest request, String payerCurrency, String receiverCurrency, UUID txnUUID, BigDecimal rate,
            BigDecimal exchangeFee, BigDecimal interTransferFee
    ) {
        return Mono.zip(
                getOrgAccount(payerCurrency, AccountType.BASE),
                getOrgAccount(payerCurrency, AccountType.FEE),
                getOrgAccount(receiverCurrency, AccountType.BASE),
                getOrgAccount(receiverCurrency, AccountType.FEE)
        ).flatMap(orgAccounts -> {
            var accountNumbers = Set.of(
                    orgAccounts.getT1().getAccountNumber(),
                    orgAccounts.getT2().getAccountNumber(),
                    orgAccounts.getT3().getAccountNumber(),
                    orgAccounts.getT4().getAccountNumber(),
                    request.getPayerAccountNumber(),
                    request.getReceiverAccountNumber()
            );

            return accountRepo.selectForUpdate(accountNumbers)
                    .flatMap(handle -> {
                        try (handle) {
                            return performCrossCurrencyTransfer(request, txnUUID, rate, exchangeFee, interTransferFee, orgAccounts, handle);
                        }
                    })
                    .switchIfEmpty(Mono.error(new RetryLaterException()));
        });
    }

    private Mono<TxnGroup> performCrossCurrencyTransfer(
            TransferRequest request, UUID txnUUID, BigDecimal rate, BigDecimal exchangeFee, BigDecimal interTransferFee,
            Tuple4<Account, Account, Account, Account> orgAccounts, AccountRepo.LockHandle handle) {
        var baseForPayer = handle.getAccounts().get(orgAccounts.getT1().getAccountNumber());
        var feeForPayer = handle.getAccounts().get(orgAccounts.getT2().getAccountNumber());
        var baseForReceiver = handle.getAccounts().get(orgAccounts.getT3().getAccountNumber());
        var feeForReceiver = handle.getAccounts().get(orgAccounts.getT4().getAccountNumber());
        var payerAccount = handle.getAccounts().get(request.getPayerAccountNumber());
        var receiverAccount = handle.getAccounts().get(request.getReceiverAccountNumber());

        var withdrawnAmount = request.getAmount();
        var boughtAmount = withdrawnAmount.subtract(exchangeFee).multiply(rate);
        var depositAmount = boughtAmount.subtract(interTransferFee);

        if (payerAccount.getBalance().compareTo(withdrawnAmount) < 0) {
            return Mono.error(new BusinessLogicError("Insufficient funds")); // todo: return failed transaction
        }

        var exchangeFeeComment = "Exchange fee for international transfer#" + txnUUID + " from " + request.getPayerAccountNumber() +
                " to " + request.getReceiverAccountNumber();
        var interTransferFeeComment = "InterTransfer fee for transfer#" + txnUUID + " from " + request.getPayerAccountNumber() +
                " to " + request.getReceiverAccountNumber();
        var exchangeComment = "Currency Exchange for nternational transfer#" + txnUUID + " from " + request.getPayerAccountNumber() +
                " to " + request.getReceiverAccountNumber();
        var payerComment = "International Transfer to " + request.getReceiverAccountNumber() + ": " + request.getComment();
        var baseComment = "International Transfer#" + txnUUID + " from " + request.getPayerAccountNumber() +
                " to " + request.getReceiverAccountNumber();

        return createGroup(request, txnUUID, TxnType.INTER_TRANSFER, payerAccount.getCurrencyCode())
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
                                        exchangeFee, exchangeFee.negate(),
                                        exchangeFeeComment, exchangeFeeComment
                                ))
                                .then(performTransfer(
                                        group, TxnSpendingType.EXCHANGE,
                                        baseForReceiver, baseForPayer,
                                        boughtAmount, withdrawnAmount.subtract(exchangeFee).negate(),
                                        exchangeComment, exchangeComment
                                ))
                                .then(performTransfer(
                                        group, TxnSpendingType.FEE,
                                        feeForReceiver, baseForReceiver,
                                        exchangeFee, exchangeFee.negate(),
                                        interTransferFeeComment, interTransferFeeComment
                                ))
                                .then(performTransfer(
                                        group, TxnSpendingType.TRANSFER,
                                        receiverAccount, baseForReceiver,
                                        depositAmount, depositAmount.negate(),
                                        baseComment, baseComment
                                ))
                                .thenReturn(group)
                );
    }

}

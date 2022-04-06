package io.shmaks.banking.service.processors;

import io.shmaks.banking.model.*;
import io.shmaks.banking.repo.AccountRepo;
import io.shmaks.banking.repo.TxnGroupRepo;
import io.shmaks.banking.repo.TxnRepo;
import io.shmaks.banking.service.dto.MoneyRequest;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

import static io.shmaks.banking.service.bookkeeping.OrgAccountsBootstrapper.ORG_ID;

abstract class BaseProcessor {

    final TxnGroupRepo txnGroupRepo;
    final TxnRepo txnRepo;
    final AccountRepo accountRepo;

    public BaseProcessor(TxnGroupRepo txnGroupRepo, TxnRepo txnRepo, AccountRepo accountRepo) {
        this.txnGroupRepo = txnGroupRepo;
        this.txnRepo = txnRepo;
        this.accountRepo = accountRepo;
    }

    Mono<Account> getOrgAccount(String currencyCode, AccountType type) {
        return accountRepo.findAllByOwnerIdOrderByAccountNumberAsc(ORG_ID, 10000, null)
                .handle((accounts, sink) ->
                        accounts.stream()
                                .filter(account -> account.getCurrencyCode().equals(currencyCode) &&
                                        account.getType() == type
                                ).findAny().ifPresent(sink::next)
                );
    }

    @SuppressWarnings("DuplicatedCode")
    Mono<Void> performTransfer(
            TxnGroup group, TxnSpendingType spendingType,
            Account depositAccount, Account creditAccount,
            BigDecimal depositAmount, BigDecimal creditAmount,
            String depositComment, String creditComment
    ) {
        var depositTxn = new Txn();
        depositTxn.setAccountId(depositAccount.getId());
        depositTxn.setAmount(depositAmount);
        depositTxn.setSpendingType(spendingType);
        depositTxn.setDetails(depositComment);
        depositTxn.setStatus(TxnStatus.SUCCESS);
        depositTxn.setTxnGroupId(group.getId());
        depositTxn.setCreatedAt(group.getCreatedAt());

        var creditTxn = new Txn();
        creditTxn.setAccountId(creditAccount.getId());
        creditTxn.setAmount(creditAmount);
        creditTxn.setSpendingType(spendingType);
        creditTxn.setDetails(creditComment);
        creditTxn.setStatus(TxnStatus.SUCCESS);
        creditTxn.setTxnGroupId(group.getId());
        creditTxn.setCreatedAt(group.getCreatedAt());

        return Mono.zip(
                        txnRepo.create(depositTxn), txnRepo.create(creditTxn)
                )
                .flatMap(tuple -> Mono.zip(
                        txnRepo.link(tuple.getT1(), tuple.getT2()),
                        accountRepo.updateBalance(depositAccount.getId(), tuple.getT1().getId(), depositAmount),
                        accountRepo.updateBalance(creditAccount.getId(), tuple.getT2().getId(), creditAmount)
                ))
                .then();
    }

    Mono<TxnGroup> createGroup(MoneyRequest request, UUID txnUUID, TxnType type, String currencyCode) {
        var txnGroup = new TxnGroup();
        txnGroup.setTxnUUID(txnUUID);
        txnGroup.setAmount(request.getAmount());
        txnGroup.setCurrencyCode(currencyCode);
        txnGroup.setComment(request.getComment());
        txnGroup.setReceiverAccountNumber(request.getReceiverAccountNumber());
        txnGroup.setPayerAccountNumber(request.getPayerAccountNumber());
        txnGroup.setType(type);
        return txnGroupRepo.merge(txnGroup).map(TxnGroupRepo.CreationResult::getTxnGroup);
    }
}

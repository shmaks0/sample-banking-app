package io.shmaks.banking.controller;

import io.shmaks.banking.service.TransferService;
import io.shmaks.banking.service.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/transfer")
@Validated
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    private final TransferService service;

    public TransferController(TransferService service) {
        this.service = service;
    }

    @PreAuthorize("hasRole('USER')")
    @PutMapping("/deposit/{txnUUID}")
    public Mono<ResponseEntity<TxnResult>> deposit(
            @RequestBody @Validated DepositRequest request,
            @AuthenticationPrincipal String ownerId,
            @PathVariable UUID txnUUID
            ) {
        log.info("deposit to account: ownerId={}, body={}, txnUUID={}", ownerId, request, txnUUID);
        return service.deposit(request, ownerId, txnUUID)
                .map(ResponseEntity::ok);
    }

    @PreAuthorize("hasRole('USER')")
    @PutMapping("/withdrawal/{txnUUID}")
    public Mono<ResponseEntity<TxnResult>> withdrawal(
            @RequestBody @Validated WithdrawalRequest request,
            @AuthenticationPrincipal String ownerId,
            @PathVariable UUID txnUUID
    ) {
        log.info("withdraw from account: ownerId={}, body={}, txnUUID={}", ownerId, request, txnUUID);
        return service.withdraw(request, ownerId, txnUUID).map(ResponseEntity::ok);
    }

    @PreAuthorize("hasRole('USER')")
    @PutMapping("/{txnUUID}")
    public Mono<ResponseEntity<TxnResult>> transfer(
            @RequestBody @Validated TransferRequest request,
            @AuthenticationPrincipal String ownerId,
            @PathVariable UUID txnUUID
    ) {
        log.info("transfer between accounts: ownerId={}, body={}, txnUUID={}", ownerId, request, txnUUID);
        return service.transfer(request, ownerId, txnUUID).map(ResponseEntity::ok);
    }
}

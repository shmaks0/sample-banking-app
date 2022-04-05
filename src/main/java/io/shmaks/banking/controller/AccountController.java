package io.shmaks.banking.controller;

import io.shmaks.banking.service.AccountService;
import io.shmaks.banking.service.dto.BalanceResponse;
import io.shmaks.banking.service.dto.CreateAccountRequest;
import io.shmaks.banking.service.dto.Pagination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import javax.validation.constraints.Max;

@RestController
@RequestMapping("/accounts")
@Validated
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @SuppressWarnings("rawtypes")
    @PreAuthorize("hasRole('USER')")
    @PostMapping
    public Mono<ResponseEntity> createAccount(
            @RequestBody @Validated CreateAccountRequest request,
            @AuthenticationPrincipal String ownerId,
            UriComponentsBuilder componentsBuilder
        ) {
        log.info("creating account: ownerId={}, body={}", ownerId, request);
        return service.create(ownerId, request)
                .map(account -> ResponseEntity.created(
                        componentsBuilder.path("/accounts/{id}/balance").buildAndExpand(account.getId()).toUri()
                ).build());
    }

    @SuppressWarnings("rawtypes")
    @PreAuthorize("hasRole('USER')")
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity> deleteAccount(@PathVariable("id") Long id, @AuthenticationPrincipal String ownerId) {
        log.info("deleting account: ownerId={}, id={}", ownerId, id);
        return service.deleteById(id, ownerId)
                //.doOnNext() // logging & metrics
                .thenReturn(ResponseEntity.noContent().build());
    }

    @SuppressWarnings("rawtypes")
    @PreAuthorize("hasRole('USER')")
    @GetMapping("/{id}/balance")
    public Mono<ResponseEntity> getBalance(@PathVariable("id") Long id, @AuthenticationPrincipal String ownerId) {
        log.info("get balance: ownerId={}, id={}", ownerId, id);
        return service.findById(id)
                .handle((account, sink) -> {
                    if (account.getOwnerId().equals(ownerId)) { // metrics & logging
                        sink.next(ResponseEntity.ok(new BalanceResponse(account.getBalance())));
                    }
                })
                .cast(ResponseEntity.class)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @SuppressWarnings("rawtypes")
    @PreAuthorize("hasRole('USER')")
    @GetMapping
    public Mono<ResponseEntity> listAccounts(
            @AuthenticationPrincipal String ownerId,
            @RequestParam(required = false) @Max(20) Integer count,
            @RequestParam(required = false) String after) {
        log.info("list accounts: ownerId={}, count={}, after={}", ownerId, count, after);
        var pagination = new Pagination<>(count != null ? count : 10, after);

        return service.findAccounts(ownerId, pagination).map(ResponseEntity::ok);
    }

    @SuppressWarnings("rawtypes")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/all")
    public Mono<ResponseEntity> listAllAccounts(
            @RequestParam(required = false) @Max(50) Integer count,
            @RequestParam(required = false) String after) {
        log.info("list accounts by admin: count={}, after={}", count, after);
        var pagination = new Pagination<>(count != null ? count : 10, after);

        return service.findAllAccounts(pagination).map(ResponseEntity::ok);
    }
}

package io.shmaks.banking.controller;

import io.shmaks.banking.service.AccountService;
import io.shmaks.banking.service.dto.CreateAccountRequest;
import io.shmaks.banking.service.dto.Pagination;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.URI;

@RestController("/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @SuppressWarnings("rawtypes")
    @PreAuthorize("hasRole('USER')")
    @PostMapping
    public Mono<ResponseEntity> createAccount(CreateAccountRequest request, @AuthenticationPrincipal String ownerId) {
        return service.create(ownerId, request)
                .map(account -> (ResponseEntity) ResponseEntity.created(URI.create("/accounts/" + account.getId())));
    }

    @SuppressWarnings("rawtypes")
    @PreAuthorize("hasRole('USER')")
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity> deleteAccount(@PathVariable("id") Long id, @AuthenticationPrincipal String ownerId) {
        return service.deleteById(id, ownerId)
                //.doOnNext() // logging & metrics
                .thenReturn(ResponseEntity.noContent().build());
    }

    @SuppressWarnings("rawtypes")
    @PreAuthorize("hasRole('USER')")
    @GetMapping("/{id}")
    public Mono<ResponseEntity> getBalance(@PathVariable("id") Long id, @AuthenticationPrincipal String ownerId) {
        return service.findById(id)
                .handle((account, sink) -> {
                    if (account.getOwnerId().equals(ownerId)) { // metrics & logging
                        sink.next(ResponseEntity.ok(account.getBalance()));
                    }
                })
                .cast(ResponseEntity.class)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @SuppressWarnings("rawtypes")
    @PreAuthorize("hasRole('USER')")
    @GetMapping
    private Mono<ResponseEntity> listAccounts(
            @AuthenticationPrincipal String ownerId,
            @RequestAttribute(required = false) Integer count,
            @RequestAttribute(required = false) String after) {

        var pagination = new Pagination<>(count != null ? count : 10, after);

        return service.findAccounts(ownerId, pagination).map(ResponseEntity::ok);
    }

    @SuppressWarnings("rawtypes")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    private Mono<ResponseEntity> listAllAccounts(
            @RequestAttribute(required = false) Integer count,
            @RequestAttribute(required = false) String after) {

        var pagination = new Pagination<>(count != null ? count : 10, after);

        return service.findAllAccounts(pagination).map(ResponseEntity::ok);
    }
}

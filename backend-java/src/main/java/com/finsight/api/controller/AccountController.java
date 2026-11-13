package com.finsight.api.controller;

import com.finsight.api.dto.AccountDtos.AccountRequest;
import com.finsight.api.dto.AccountDtos.AccountResponse;
import com.finsight.api.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts", description = "Bank accounts and wallets")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @Operation(summary = "List accounts with computed balances")
    public List<AccountResponse> list(@AuthenticationPrincipal UUID userId) {
        return accountService.list(userId);
    }

    @PostMapping
    @Operation(summary = "Create an account")
    public ResponseEntity<AccountResponse> create(@AuthenticationPrincipal UUID userId,
                                                  @Valid @RequestBody AccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(userId, request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an account")
    public AccountResponse update(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
                                  @Valid @RequestBody AccountRequest request) {
        return accountService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Archive an account")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        accountService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}

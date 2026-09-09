package io.github.aminedelta.core_banking.controller;

import io.github.aminedelta.core_banking.dto.AccountResponse;
import io.github.aminedelta.core_banking.dto.CreateAccountRequest;
import io.github.aminedelta.core_banking.dto.TransactionHistoryResponse;
import io.github.aminedelta.core_banking.service.AccountService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        List<AccountResponse> accounts = accountService.getAllAccounts();
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable UUID accountId) {
        BigDecimal balance = accountService.getBalance(accountId);
        return ResponseEntity.ok(balance);
    }

    @GetMapping("/{accountId}/transactions")
    public ResponseEntity<List<TransactionHistoryResponse>> getTransactionHistory(@PathVariable UUID accountId) {
        return ResponseEntity.ok(accountService.getTransactionHistory(accountId));
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@RequestBody CreateAccountRequest request) {
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
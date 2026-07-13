package io.github.aminedelta.core_banking.controller;

import io.github.aminedelta.core_banking.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController //Java Reflection ??
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accountService;

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<String> getBalance(@PathVariable String accountId) {
        try {
            return ResponseEntity.ok(accountService.getBalance(accountId).toString());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("An unexpected error occurred: " + e.getMessage());
        }
    }
}

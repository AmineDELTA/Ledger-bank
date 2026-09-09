package io.github.aminedelta.core_banking.exception;

import java.util.UUID;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String message) {
        super(message);
    }

    public AccountNotFoundException(UUID accountId) {
        super("Account not found with ID: " + accountId);
    }
}

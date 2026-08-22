package io.github.aminedelta.core_banking.service;

import io.github.aminedelta.core_banking.domain.EntryType;
import io.github.aminedelta.core_banking.domain.IdempotentRequest;
import io.github.aminedelta.core_banking.domain.LedgerEntry;
import io.github.aminedelta.core_banking.domain.TransactionHeader;
import io.github.aminedelta.core_banking.dto.TransferResult;
import io.github.aminedelta.core_banking.exception.InsufficientFundsException;
import io.github.aminedelta.core_banking.repository.AccountRepository;
import io.github.aminedelta.core_banking.repository.LedgerEntryRepository;
import io.github.aminedelta.core_banking.repository.TransactionHeaderRepository;
import io.github.aminedelta.core_banking.repository.IdempotentRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aminedelta.core_banking.domain.Account;
import java.util.Optional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransactionHeaderRepository transactionHeaderRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final AccountService accountService;
    private final IdempotentRequestRepository idempotencyRepository;
    private final ObjectMapper objectMapper;
    @Transactional
    public TransferResult transfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String description, String idempotencyKey) {

        if (idempotencyKey != null) {
            Optional<IdempotentRequest> existingRequest = idempotencyRepository.findById(idempotencyKey);
            if (existingRequest.isPresent()) {
                return new TransferResult(
                    UUID.fromString(existingRequest.get().getResponseBody()), 
                    "SUCCESS", 
                    "Duplicate request: returning previous result"
                );
            }
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (fromAccountId == null || toAccountId == null) {
            throw new IllegalArgumentException("Account IDs cannot be null");
        }
        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }

        // 1. Deterministic Lock Ordering to prevent PostgreSQL deadlocks
        UUID firstLockId = fromAccountId.compareTo(toAccountId) < 0 ? fromAccountId : toAccountId;
        UUID secondLockId = fromAccountId.compareTo(toAccountId) < 0 ? toAccountId : fromAccountId;

        Account firstLockAccount = accountRepository.findAndLockById(firstLockId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + firstLockId));
        Account secondLockAccount = accountRepository.findAndLockById(secondLockId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + secondLockId));

        Account fromAccount = (firstLockAccount.getId().equals(fromAccountId)) ? firstLockAccount : secondLockAccount;
        Account toAccount = (firstLockAccount.getId().equals(toAccountId)) ? firstLockAccount : secondLockAccount;

        // 2. Check balance against locked state
        if (accountService.getBalance(fromAccountId).compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds in the from account");
        }

        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));
        // 3. Create double-entry records
        TransactionHeader savedHeader = transactionHeaderRepository.save(new TransactionHeader(description));

        LedgerEntry debitEntry = new LedgerEntry();
        debitEntry.setAccountId(fromAccountId);
        debitEntry.setAmount(amount.negate());
        debitEntry.setTransactionHeader(savedHeader);
        debitEntry.setType(EntryType.DEBIT);
        ledgerRepository.save(debitEntry);

        LedgerEntry creditEntry = new LedgerEntry();
        creditEntry.setAccountId(toAccountId);
        creditEntry.setAmount(amount);
        creditEntry.setTransactionHeader(savedHeader);
        creditEntry.setType(EntryType.CREDIT);
        ledgerRepository.save(creditEntry);

        TransferResult result = new TransferResult(
        savedHeader.getId(), 
        "SUCCESS",
        "Transfer completed successfully"
        );

    // Save the JSON representation in the idempotency table
        if (idempotencyKey != null) {
            try {
                idempotencyRepository.save(new IdempotentRequest(
                    idempotencyKey, 
                    200, 
                    objectMapper.writeValueAsString(result) // Store as JSON string in DB
                ));
            } catch (JsonProcessingException e) {
                // If Jackson fails to convert the object to JSON, crash the transaction cleanly
                throw new RuntimeException("Failed to serialize transfer result", e);
            }
        }
        return result;
    }
}
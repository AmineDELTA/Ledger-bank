package io.github.aminedelta.core_banking.service;

import io.github.aminedelta.core_banking.domain.EntryType;
import io.github.aminedelta.core_banking.domain.IdempotentRequest;
import io.github.aminedelta.core_banking.domain.LedgerEntry;
import io.github.aminedelta.core_banking.domain.TransactionHeader;
import io.github.aminedelta.core_banking.dto.TransferResult;
import io.github.aminedelta.core_banking.exception.AccountNotFoundException;
import io.github.aminedelta.core_banking.exception.InsufficientFundsException;
import io.github.aminedelta.core_banking.repository.AccountRepository;
import io.github.aminedelta.core_banking.repository.LedgerEntryRepository;
import io.github.aminedelta.core_banking.repository.TransactionHeaderRepository;
import io.github.aminedelta.core_banking.repository.IdempotentRequestRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import io.github.aminedelta.core_banking.aop.AuditLog;

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
    private final IdempotentRequestRepository idempotencyRepository;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @AuditLog
    @Transactional
        public TransferResult transfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String description,
            String idempotencyKey) {

        if (idempotencyKey != null) {
            Optional<IdempotentRequest> existingRequest = idempotencyRepository.findById(idempotencyKey);
            
            if (existingRequest.isPresent()) {
                try {
                    TransferResult result = objectMapper.readValue(existingRequest.get().getResponseBody(), TransferResult.class);
                    cacheReceipt(idempotencyKey, existingRequest.get().getResponseBody());
                    return result;
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to deserialize previous transfer result", e);
                }
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

        UUID firstLockId = fromAccountId.compareTo(toAccountId) < 0 ? fromAccountId : toAccountId;
        UUID secondLockId = fromAccountId.compareTo(toAccountId) < 0 ? toAccountId : fromAccountId;

        Account firstLockAccount = accountRepository.findAndLockById(firstLockId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + firstLockId));
        Account secondLockAccount = accountRepository.findAndLockById(secondLockId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + secondLockId));

        Account fromAccount = (firstLockAccount.getId().equals(fromAccountId)) ? firstLockAccount : secondLockAccount;
        Account toAccount = (firstLockAccount.getId().equals(toAccountId)) ? firstLockAccount : secondLockAccount;

        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds in the from account");
        }

        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));
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

        if (idempotencyKey != null) {
            try {
                String jsonReceipt = objectMapper.writeValueAsString(result);
                idempotencyRepository.save(new IdempotentRequest(
                    idempotencyKey, 
                    200, 
                    jsonReceipt
                ));
                cacheReceiptAfterCommit(idempotencyKey, jsonReceipt);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize transfer result", e);
            }
        }
        return result;
    }

    private void cacheReceiptAfterCommit(String idempotencyKey, String jsonReceipt) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cacheReceipt(idempotencyKey, jsonReceipt);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cacheReceipt(idempotencyKey, jsonReceipt);
            }
        });
    }

    private void cacheReceipt(String idempotencyKey, String jsonReceipt) {
        try {
            idempotencyService.saveReceipt(idempotencyKey, jsonReceipt);
        } catch (RuntimeException ignored) {
        }
    }
}
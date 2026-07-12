package io.github.aminedelta.core_banking.service;

import io.github.aminedelta.core_banking.domain.*;
import io.github.aminedelta.core_banking.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferService {
    private final AccountRepository accountRepository;
    private final TransactionHeaderRepository transactionHeaderRepository;
    private final LedgerEntryRepository ledgerRepository;

    @Transactional
    public void transfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String description) {

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }

        if (!accountRepository.existsById(fromAccountId)) {
            throw new IllegalArgumentException("From account not found");
        }
        if (!accountRepository.existsById(toAccountId)) {
            throw new IllegalArgumentException("To account not found");
        }

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
    }
}
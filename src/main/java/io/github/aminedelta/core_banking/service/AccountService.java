package io.github.aminedelta.core_banking.service;

import io.github.aminedelta.core_banking.domain.*;
import io.github.aminedelta.core_banking.repository.AccountRepository;
import io.github.aminedelta.core_banking.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional(readOnly = true)
    public Account getBalance(String accountId){
        return accountRepository.findById(UUID.fromString(accountId))
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(UUID accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new IllegalArgumentException("Account not found with ID: " + accountId);
        }
        // Use the optimized repository method to fetch entries for the specific account
        List<LedgerEntry> entries = ledgerEntryRepository.findByAccountId(accountId);
        BigDecimal balance = BigDecimal.ZERO;
        for (LedgerEntry entry : entries) {
            if (entry.getType() == EntryType.CREDIT) {
                balance = balance.add(entry.getAmount());
            } else if (entry.getType() == EntryType.DEBIT) {
                // Since debits are already stored as negative amounts, we add them to reduce the balance
                balance = balance.add(entry.getAmount());
            }
        }
        return balance;
    }
}

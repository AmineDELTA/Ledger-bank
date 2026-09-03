package io.github.aminedelta.core_banking.service;

import io.github.aminedelta.core_banking.domain.*;
import io.github.aminedelta.core_banking.dto.CreateAccountRequest;
import io.github.aminedelta.core_banking.dto.AccountResponse;
import io.github.aminedelta.core_banking.repository.AccountRepository;
import io.github.aminedelta.core_banking.repository.LedgerEntryRepository;
import io.github.aminedelta.core_banking.repository.TransactionHeaderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {
    
    private final AccountRepository accountRepository;
    private final TransactionHeaderRepository transactionHeaderRepository;
    private final LedgerEntryRepository ledgerRepository;

    @Transactional(readOnly = true)
    public BigDecimal getBalance(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found with ID: " + accountId));
        return account.getBalance();
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        // 1. Instantiate using your strict entity constructor
        Account account = new Account(request.getAccountNumber(), request.getHolderName());
        
        if (request.getInitialBalance() != null) {
            account.setBalance(request.getInitialBalance());
        }

        Account savedAccount = accountRepository.save(account);//???????

        // 2. The Genesis Deposit for double-entry compliance
        if (savedAccount.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            TransactionHeader header = new TransactionHeader("Genesis Deposit for " + request.getHolderName());
            transactionHeaderRepository.save(header);

            LedgerEntry creditEntry = new LedgerEntry();
            creditEntry.setAccountId(savedAccount.getId());
            creditEntry.setAmount(savedAccount.getBalance());
            creditEntry.setTransactionHeader(header);
            creditEntry.setType(EntryType.CREDIT);
            ledgerRepository.save(creditEntry);
        }

        return new AccountResponse(
            savedAccount.getId(), 
            savedAccount.getAccountNumber(), 
            savedAccount.getHolderName(), 
            savedAccount.getBalance()
        );
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(account -> new AccountResponse(
                        account.getId(),
                        account.getAccountNumber(),
                        account.getHolderName(),
                        account.getBalance()
                ))
                .toList();
    }
}
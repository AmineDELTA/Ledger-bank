package io.github.aminedelta.core_banking.service;

import io.github.aminedelta.core_banking.domain.Account;
import io.github.aminedelta.core_banking.domain.EntryType;
import io.github.aminedelta.core_banking.domain.LedgerEntry;
import io.github.aminedelta.core_banking.domain.TransactionHeader;
import io.github.aminedelta.core_banking.exception.InsufficientFundsException;
import io.github.aminedelta.core_banking.repository.AccountRepository;
import io.github.aminedelta.core_banking.repository.LedgerEntryRepository;
import io.github.aminedelta.core_banking.repository.TransactionHeaderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class TransferServiceTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TransactionHeaderRepository transactionHeaderRepository;

    @Autowired
    private AccountService accountService;

    private UUID accountAId;
    private UUID accountBId;

    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAll();
        transactionHeaderRepository.deleteAll();
        accountRepository.deleteAll();

        Account accountA = accountRepository.save(new Account("ACC-001", "Alice"));
        Account accountB = accountRepository.save(new Account("ACC-002", "Bob"));

        accountAId = accountA.getId();
        accountBId = accountB.getId();

        seedInitialBalance(accountAId, new BigDecimal("1000.00"));
        seedInitialBalance(accountBId, new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("Single transfer updates balances correctly")
    void testSuccessfulTransfer() {
        transferService.transfer(accountAId, accountBId, new BigDecimal("200.00"), "test transfer", null);

        assertEquals(0, new BigDecimal("800.00").compareTo(accountService.getBalance(accountAId)));
        assertEquals(0, new BigDecimal("1200.00").compareTo(accountService.getBalance(accountBId)));
    }

    @Test
    @DisplayName("Insufficient funds throws and does not change balances")
    void testInsufficientFunds() {
        assertThrows(InsufficientFundsException.class, () ->
                transferService.transfer(accountAId, accountBId, new BigDecimal("1500.00"), "test transfer", null)
        );

        assertEquals(0, new BigDecimal("1000.00").compareTo(accountService.getBalance(accountAId)));
        assertEquals(0, new BigDecimal("1000.00").compareTo(accountService.getBalance(accountBId)));
    }

    @Test
    @DisplayName("Invalid input rejects zero or negative amounts")
    void testNegativeOrZeroAmount() {
        assertThrows(IllegalArgumentException.class, () ->
                transferService.transfer(accountAId, accountBId, new BigDecimal("-50.00"), "invalid transfer", null)
        );
    }

    @Test
    @DisplayName("Concurrent transfers preserve final balance")
    void testConcurrentTransfers() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch raceGate = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    raceGate.await();
                    transferService.transfer(accountAId, accountBId, new BigDecimal("10.00"), "concurrent transfer", null);
                } catch (Exception ignored) {
                    //lock timeout or rollback may occur
                } finally {
                    finishLine.countDown();
                }
            });
        }

        raceGate.countDown();
        finishLine.await();
        executor.shutdown();

        assertEquals(0, new BigDecimal("500.00").compareTo(accountService.getBalance(accountAId)));
        assertEquals(0, new BigDecimal("1500.00").compareTo(accountService.getBalance(accountBId)));
    }

    private void seedInitialBalance(UUID accountId, BigDecimal amount) {
        TransactionHeader header = transactionHeaderRepository.save(new TransactionHeader("initial deposit"));

        LedgerEntry entry = new LedgerEntry();
        entry.setTransactionHeader(header);
        entry.setAccountId(accountId);
        entry.setType(EntryType.CREDIT);
        entry.setAmount(amount);
        ledgerEntryRepository.save(entry);

        Account account = accountRepository.findById(accountId).orElseThrow();
        account.setBalance(amount);
        accountRepository.save(account);
    }
}
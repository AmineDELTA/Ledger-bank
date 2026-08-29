package io.github.aminedelta.core_banking.service;

import io.github.aminedelta.core_banking.domain.Account;
import io.github.aminedelta.core_banking.repository.AccountRepository;
import io.github.aminedelta.core_banking.repository.LedgerEntryRepository;
import io.github.aminedelta.core_banking.repository.TransactionHeaderRepository;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.List;

@SpringBootTest
public class TransferServiceConcurrencyTest {
    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TransactionHeaderRepository transactionHeaderRepository;

    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAll();
        transactionHeaderRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    public void testConcurrentTransfers() throws InterruptedException, ExecutionException {

        Account accountA = new Account("TEST-ACC-001", "Holder A");
        accountA.setBalance(new BigDecimal("1000.00"));
        final Account finalAccountA = accountRepository.save(accountA); // Database assigns the UUID here

        Account accountB = new Account("TEST-ACC-002", "Holder B");
        accountB.setBalance(new BigDecimal("0.00"));
        final Account finalAccountB = accountRepository.save(accountB);

        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        List<Future<Exception>> futures = new ArrayList<>();

        for (int i=0; i < threadCount; i++) {
            Future<Exception> future = executorService.submit(() -> {
                try {
                    latch.await(); // Wait for the latch to be released
                    transferService.transfer(
                        finalAccountA.getId(),
                        finalAccountB.getId(),
                        new java.math.BigDecimal("10.00"),
                        "Concurrent Transfer",
                        null
                    );
                    return null; // No exception, transfer successful
                } catch (Exception e) {
                    e.printStackTrace();
                    return e;
                } finally {
                    endLatch.countDown(); // Signal that this thread has finished
                }
            });
            futures.add(future);
        }
        latch.countDown(); // Release the latch to start all threads

        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        assertEquals(true, completed, "Not all threads completed in time");

        for (Future<Exception> future : futures) {
            Exception e = future.get();
            if (e != null) {
                System.err.println("Thread failed with exception: " + e.getMessage());
            }
        }

        Account actualAccountA = accountRepository.findById(finalAccountA.getId()).orElseThrow();
        Account actualAccountB = accountRepository.findById(finalAccountB.getId()).orElseThrow();

        assertEquals(0, new BigDecimal("500.00").compareTo(actualAccountA.getBalance()), "Account A balance is incorrect");
        
        assertEquals(0, new BigDecimal("500.00").compareTo(actualAccountB.getBalance()), "Account B balance is incorrect");
        
        executorService.shutdown();
    }
}

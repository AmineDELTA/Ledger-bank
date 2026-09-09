package io.github.aminedelta.core_banking.controller;

import io.github.aminedelta.core_banking.domain.EntryType;
import io.github.aminedelta.core_banking.dto.TransactionHistoryResponse;
import io.github.aminedelta.core_banking.service.AccountService;
import io.github.aminedelta.core_banking.service.IdempotencyService;
import io.github.aminedelta.core_banking.service.TransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @MockBean
    private TransferService transferService;

    @MockBean
    private IdempotencyService idempotencyService;

    @Test
    @DisplayName("GET /accounts/{accountId}/transactions returns ledger history")
    void getTransactionHistory_ReturnsEntries() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID ledgerEntryId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        LocalDateTime timestamp = LocalDateTime.of(2026, 9, 9, 13, 0, 0);

        when(accountService.getTransactionHistory(accountId)).thenReturn(List.of(
                new TransactionHistoryResponse(
                        ledgerEntryId,
                        transactionId,
                        timestamp,
                        "history transfer",
                        EntryType.DEBIT,
                        new BigDecimal("200.00")
                )
        ));

        mockMvc.perform(get("/accounts/{accountId}/transactions", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ledgerEntryId").value(ledgerEntryId.toString()))
                .andExpect(jsonPath("$[0].transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$[0].timestamp").value("2026-09-09T13:00:00"))
                .andExpect(jsonPath("$[0].description").value("history transfer"))
                .andExpect(jsonPath("$[0].type").value("DEBIT"))
                .andExpect(jsonPath("$[0].amount").value(200.00));
    }

    @Test
    @DisplayName("GET /accounts/{accountId}/transactions returns 404 for unknown accounts")
    void getTransactionHistory_UnknownAccount_ReturnsNotFound() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(accountService.getTransactionHistory(accountId))
                .thenThrow(new IllegalArgumentException("Account not found with ID: " + accountId));

        mockMvc.perform(get("/accounts/{accountId}/transactions", accountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND"));
    }
}

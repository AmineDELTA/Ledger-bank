package io.github.aminedelta.core_banking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.aminedelta.core_banking.domain.IdempotentRequest;
import io.github.aminedelta.core_banking.dto.TransferRequest;
import io.github.aminedelta.core_banking.exception.InsufficientFundsException;
import io.github.aminedelta.core_banking.service.TransferService;
import io.github.aminedelta.core_banking.service.IdempotencyService;
import io.github.aminedelta.core_banking.repository.IdempotentRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.Optional;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransferService transferService;

    @MockBean
    private IdempotentRequestRepository idempotencyRepository;

    @MockBean
    private IdempotencyService idempotencyService;

    @Test
    @DisplayName("POST /api/transfers - Valid request returns 200 OK")
    void testSuccessfulTransferEndpoint() throws Exception {
        TransferRequest request = new TransferRequest(
            UUID.randomUUID(), 
            UUID.randomUUID(), 
            new BigDecimal("100.00"),
            "Test Transfer"
        );

        mockMvc.perform(post("/transfers")
            .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/transfers - Insufficient funds returns 400 Bad Request")
    void testInsufficientFundsEndpoint() throws Exception {
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("5000.00");
        String desc = "Overdraft Test";

        TransferRequest request = new TransferRequest(fromId, toId, amount, desc);

        String idempotencyKey = UUID.randomUUID().toString();
        when(idempotencyService.tryLock(idempotencyKey)).thenReturn(true);
        doThrow(new InsufficientFundsException("Insufficient balance"))
            .when(transferService).transfer(fromId, toId, amount, desc, idempotencyKey);

        mockMvc.perform(post("/transfers")
            .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /transfers - New idempotency key executes transfer and saves result")
    void executeTransfer_NewKey_ExecutesTransferAndSavesKey() throws Exception {
        
        String idempotencyKey = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00"), "Payment");

        // FIX 1: Mock the service to return a successful TransferResult
        io.github.aminedelta.core_banking.dto.TransferResult mockResult = 
            new io.github.aminedelta.core_banking.dto.TransferResult(UUID.randomUUID(), "SUCCESS", "Transfer completed successfully");
        
        when(transferService.transfer(any(), any(), any(), any(), any())).thenReturn(mockResult);
        when(idempotencyService.tryLock(idempotencyKey)).thenReturn(true);

        mockMvc.perform(post("/transfers")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                // FIX 2: Assert against the JSON object's "message" field, not the raw string
                .andExpect(jsonPath("$.message").value("Transfer completed successfully"));

        verify(transferService, times(1)).transfer(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /transfers - Duplicate key returns cached response and skips transfer execution")
    void executeTransfer_DuplicateKey_ReturnsCachedResponseAndSkipsTransfer() throws Exception {
        
        String idempotencyKey = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00"), "Payment");
        IdempotentRequest completedRequest = new IdempotentRequest(idempotencyKey, 200, "Transfer completed successfully");

        // FIX 3: Mock the SERVICE to throw the database constraint violation
        when(transferService.transfer(any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("Primary key violation"));
        when(idempotencyService.tryLock(idempotencyKey)).thenReturn(false);
        when(idempotencyService.getCachedReceipt(idempotencyKey)).thenReturn(null);

        when(idempotencyRepository.findById(idempotencyKey)).thenReturn(Optional.of(completedRequest));

        mockMvc.perform(post("/transfers")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Transfer completed successfully"));
    }

    @Test
    @DisplayName("POST /transfers - Concurrent duplicate key returns 409 Conflict")
    void executeTransfer_ConcurrentDuplicateKey_ReturnsConflictAndSkipsTransfer() throws Exception {
        
        String idempotencyKey = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00"), "Payment");
        IdempotentRequest pendingRequest = new IdempotentRequest(idempotencyKey, null, null);

        // FIX 4: Mock the SERVICE to throw the database constraint violation
        when(transferService.transfer(any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("Primary key violation"));
        when(idempotencyService.tryLock(idempotencyKey)).thenReturn(false);
        when(idempotencyService.getCachedReceipt(idempotencyKey)).thenReturn(null);

        when(idempotencyRepository.findById(idempotencyKey)).thenReturn(Optional.of(pendingRequest));

        mockMvc.perform(post("/transfers")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().string("Request is currently being processed."));
    }

    @Test
    @DisplayName("POST /transfers - Redis receipt returns structured JSON")
    void executeTransfer_RedisReceiptReturnsTransferResult() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00"), "Payment");
        String receipt = "{\"transactionId\":\"" + UUID.randomUUID() + "\",\"status\":\"SUCCESS\",\"message\":\"Transfer completed successfully\"}";

        when(idempotencyService.tryLock(idempotencyKey)).thenReturn(false);
        when(idempotencyService.getCachedReceipt(idempotencyKey)).thenReturn(receipt);

        mockMvc.perform(post("/transfers")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Transfer completed successfully"));

        verify(transferService, times(0)).transfer(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /transfers - Redis pending key returns 409 Conflict")
    void executeTransfer_RedisPendingReturnsConflict() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00"), "Payment");

        when(idempotencyService.tryLock(idempotencyKey)).thenReturn(false);
        when(idempotencyService.getCachedReceipt(idempotencyKey)).thenReturn("PENDING");

        mockMvc.perform(post("/transfers")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().string("Request is currently being processed."));

        verify(transferService, times(0)).transfer(any(), any(), any(), any(), any());
    }
}
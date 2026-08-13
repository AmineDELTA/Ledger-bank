package io.github.aminedelta.core_banking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.aminedelta.core_banking.domain.IdempotentRequest;
import io.github.aminedelta.core_banking.dto.TransferRequest;
import io.github.aminedelta.core_banking.exception.InsufficientFundsException;
import io.github.aminedelta.core_banking.service.TransferService;
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
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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

        doThrow(new InsufficientFundsException("Insufficient balance"))
                .when(transferService).transfer(fromId, toId, amount, desc);

        mockMvc.perform(post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /transfers - New idempotency key executes transfer and saves result")
    void executeTransfer_NewKey_ExecutesTransferAndSavesKey() throws Exception {
        
        String idempotencyKey = UUID.randomUUID().toString();
        
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        
        TransferRequest request = new TransferRequest(
                fromAccountId, 
                toAccountId, 
                new BigDecimal("100.00"), 
                "Payment"
        );

        when(idempotencyRepository.saveAndFlush(any(IdempotentRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/transfers")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                
                .andExpect(status().isOk())
                .andExpect(content().string("Transfer completed successfully"));

        verify(transferService, times(1)).transfer(any(), any(), any(), any());
        
        verify(idempotencyRepository, times(1)).saveAndFlush(any(IdempotentRequest.class));
        verify(idempotencyRepository, times(1)).save(any(IdempotentRequest.class));
    }

    @Test
    @DisplayName("POST /transfers - Duplicate key returns cached response and skips transfer execution")
    void executeTransfer_DuplicateKey_ReturnsCachedResponseAndSkipsTransfer() throws Exception {
        
        String idempotencyKey = UUID.randomUUID().toString();
        
        TransferRequest request = new TransferRequest(
                UUID.randomUUID(), 
                UUID.randomUUID(), 
                new BigDecimal("100.00"), 
                "Payment"
        );

        //build a fake DB record representing a previously COMPLETED transaction
        IdempotentRequest completedRequest = new IdempotentRequest(
                idempotencyKey, 
                200, 
                "Transfer completed successfully"
        );

        // simulate DB collision: saveAndFlush fails because the key already exists
        when(idempotencyRepository.saveAndFlush(any(IdempotentRequest.class)))
                .thenThrow(new DataIntegrityViolationException("Primary key violation"));

        //simulate DB lookup: finding the key returns the completed record
        when(idempotencyRepository.findById(idempotencyKey))
                .thenReturn(Optional.of(completedRequest));

        mockMvc.perform(post("/transfers")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                
                .andExpect(status().isOk())
                .andExpect(content().string("Transfer completed successfully"));

        verify(transferService, never()).transfer(any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /transfers - Concurrent duplicate key returns 409 Conflict")
    void executeTransfer_ConcurrentDuplicateKey_ReturnsConflictAndSkipsTransfer() throws Exception {
        
        String idempotencyKey = UUID.randomUUID().toString();
        
        TransferRequest request = new TransferRequest(
                UUID.randomUUID(), 
                UUID.randomUUID(), 
                new BigDecimal("100.00"), 
                "Payment"
        );

        //build a fake DB record representing an IN-PROGRESS transaction
        //status code is NULL because Thread A has not finished the transfer yet
        IdempotentRequest pendingRequest = new IdempotentRequest(
                idempotencyKey, 
                null, 
                null
        );

        //simulate DB collision: Thread B fails to insert the key
        when(idempotencyRepository.saveAndFlush(any(IdempotentRequest.class)))
                .thenThrow(new DataIntegrityViolationException("Primary key violation"));

        //simulate DB lookup: Thread B finds Thread A's unfinished record
        when(idempotencyRepository.findById(idempotencyKey))
                .thenReturn(Optional.of(pendingRequest));

        mockMvc.perform(post("/transfers")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                
                .andExpect(status().isConflict())
                .andExpect(content().string("Request is currently being processed."));

        //verify Thread B did not attempt to execute the transfer
        verify(transferService, never()).transfer(any(), any(), any(), any());
    }
}
package io.github.aminedelta.core_banking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aminedelta.core_banking.dto.TransferRequest;
import io.github.aminedelta.core_banking.exception.InsufficientFundsException;
import io.github.aminedelta.core_banking.service.TransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransferService transferService;

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
}
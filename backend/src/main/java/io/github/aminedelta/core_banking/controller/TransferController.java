package io.github.aminedelta.core_banking.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aminedelta.core_banking.dto.TransferRequest;
import io.github.aminedelta.core_banking.dto.TransferResult;
import io.github.aminedelta.core_banking.domain.IdempotentRequest;
import io.github.aminedelta.core_banking.exception.InsufficientFundsException;
import io.github.aminedelta.core_banking.service.IdempotencyService;
import io.github.aminedelta.core_banking.service.TransferService;
import io.github.aminedelta.core_banking.repository.IdempotentRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransferController {
    
    private final TransferService transferService;
    private final IdempotencyService idempotencyService;
    private final IdempotentRequestRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<?> executeTransfer(
            @RequestBody TransferRequest request, 
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey) {

        try {
            // 1. Call the service (this goes inside the try block)
            if (!idempotencyKey.isBlank()) {
                try {
                    // Redis is a fast guard; PostgreSQL remains the durable fallback.
                    if (!idempotencyService.tryLock(idempotencyKey)) {
                        String cachedReceipt = idempotencyService.getCachedReceipt(idempotencyKey);
                        if ("PENDING".equals(cachedReceipt)) {
                            return ResponseEntity.status(409).body("Request is currently being processed.");
                        } else if (cachedReceipt != null) {
                            return cachedReceipt(cachedReceipt, 200);
                        }
                    }
                } catch (RuntimeException ignored) {
                    // Continue with PostgreSQL if Redis is unavailable.
                }
            } else {
                return ResponseEntity.badRequest().body("Idempotency-Key header cannot be blank.");
            }
            TransferResult result = transferService.transfer(
                request.getFromAccountId(),
                request.getToAccountId(),
                request.getAmount(),
                request.getDescription(),
                idempotencyKey
            );
            
            // 2. Return the successful result
            return ResponseEntity.ok(result);

        } catch (DataIntegrityViolationException e) {
            IdempotentRequest cachedRequest = idempotencyRepository.findById(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Idempotency record was not found"));

            if (cachedRequest.getResponseStatusCode() == null) {
                return ResponseEntity.status(409).body("Request is currently being processed.");
            }
                return cachedReceipt(cachedRequest.getResponseBody(), cachedRequest.getResponseStatusCode());
        } catch (InsufficientFundsException | IllegalArgumentException e) {
            releaseRedisKey(idempotencyKey);
            return ResponseEntity.badRequest().body(e.getMessage());
            
        } catch (Exception e) {
            releaseRedisKey(idempotencyKey);
            return ResponseEntity.status(500).body("An unexpected error occurred: " + e.getMessage());
        }
    }

    private void releaseRedisKey(String idempotencyKey) {
        try {
            idempotencyService.release(idempotencyKey);
        } catch (RuntimeException ignored) {
            // Redis cleanup must not hide the transfer error.
        }
    }

    private ResponseEntity<?> cachedReceipt(String jsonReceipt, int statusCode) {
        try {
            TransferResult result = objectMapper.readValue(jsonReceipt, TransferResult.class);
            return ResponseEntity.status(statusCode).body(result);
        } catch (JsonProcessingException e) {
            return ResponseEntity.internalServerError().body("Cached transfer receipt is invalid.");
        }
    }
}
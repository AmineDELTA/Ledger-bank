package io.github.aminedelta.core_banking.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aminedelta.core_banking.domain.IdempotentRequest;
import io.github.aminedelta.core_banking.dto.ErrorResponse;
import io.github.aminedelta.core_banking.dto.TransferRequest;
import io.github.aminedelta.core_banking.dto.TransferResult;
import io.github.aminedelta.core_banking.repository.IdempotentRequestRepository;
import io.github.aminedelta.core_banking.service.IdempotencyService;
import io.github.aminedelta.core_banking.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

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

        if (request == null) {
            throw new IllegalArgumentException("Request body cannot be null.");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header cannot be blank.");
        }

        try {
            if (!idempotencyService.tryLock(idempotencyKey)) {
                String cachedReceipt = idempotencyService.getCachedReceipt(idempotencyKey);
                if ("PENDING".equals(cachedReceipt)) {
                    ErrorResponse pendingResponse = new ErrorResponse(
                            "Request is currently being processed.",
                            "CONCURRENT_REQUEST",
                            Instant.now().toString(),
                            HttpStatus.CONFLICT.value()
                    );
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(pendingResponse);
                } else if (cachedReceipt != null) {
                    return cachedReceipt(cachedReceipt, 200);
                }
            }
        } catch (RuntimeException ignored) {
        }

        try {
            TransferResult result = transferService.transfer(
                request.getFromAccountId(),
                request.getToAccountId(),
                request.getAmount(),
                request.getDescription(),
                idempotencyKey
            );
            
            return ResponseEntity.ok(result);

        } catch (DataIntegrityViolationException e) {
            IdempotentRequest cachedRequest = idempotencyRepository.findById(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Idempotency record was not found"));

            if (cachedRequest.getResponseStatusCode() == null) {
                ErrorResponse pendingResponse = new ErrorResponse(
                        "Request is currently being processed.",
                        "CONCURRENT_REQUEST",
                        Instant.now().toString(),
                        HttpStatus.CONFLICT.value()
                );
                return ResponseEntity.status(HttpStatus.CONFLICT).body(pendingResponse);
            }
            return cachedReceipt(cachedRequest.getResponseBody(), cachedRequest.getResponseStatusCode());
        } catch (RuntimeException e) {
            releaseRedisKey(idempotencyKey);
            throw e;
        }
    }

    private void releaseRedisKey(String idempotencyKey) {
        try {
            idempotencyService.release(idempotencyKey);
        } catch (RuntimeException ignored) {
        }
    }

    private ResponseEntity<?> cachedReceipt(String jsonReceipt, int statusCode) {
        if (jsonReceipt == null || jsonReceipt.isBlank()) {
            return ResponseEntity.status(statusCode).build();
        }
        try {
            TransferResult result = objectMapper.readValue(jsonReceipt, TransferResult.class);
            return ResponseEntity.status(statusCode).body(result);
        } catch (JsonProcessingException e) {
            ErrorResponse errorResponse = new ErrorResponse(
                    "Cached transfer receipt is invalid.",
                    "INTERNAL_SERVER_ERROR",
                    Instant.now().toString(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
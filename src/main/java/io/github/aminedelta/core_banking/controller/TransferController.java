package io.github.aminedelta.core_banking.controller;

import io.github.aminedelta.core_banking.domain.IdempotentRequest;
import io.github.aminedelta.core_banking.dto.TransferRequest;
import io.github.aminedelta.core_banking.exception.InsufficientFundsException;
import io.github.aminedelta.core_banking.repository.IdempotentRequestRepository;
import io.github.aminedelta.core_banking.service.TransferService;
import io.github.aminedelta.core_banking.dto.TransferResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransferController {
    
    private final TransferService transferService;
    private final IdempotentRequestRepository idempotencyRepository;

    @PostMapping
    public ResponseEntity<?> executeTransfer(
            @RequestBody TransferRequest request, 
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        try {
            // 1. Call the service (this goes inside the try block)
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
            // EDGE CASE: Thread B hit the controller at the exact millisecond as Thread A.
            // Thread A won, committed the transaction, and saved the key. 
            // Thread B's transaction blew up on the Unique Primary Key constraint and rolled back safely.
            IdempotentRequest cachedRequest = idempotencyRepository.findById(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Key should exist here"));
            
            if (cachedRequest.getResponseStatusCode() == null) {
                return ResponseEntity.status(409).body("Request is currently being processed.");
            }
            // We just fetch Thread A's success result and give it to Thread B!
            return ResponseEntity.status(cachedRequest.getResponseStatusCode())
                                 .body(cachedRequest.getResponseBody());

        } catch (InsufficientFundsException | IllegalArgumentException e) {
            // Note: Because these throw an exception, the @Transactional rolls back. 
            // The idempotency key is deliberately NOT saved, allowing the user to deposit funds and retry.
            return ResponseEntity.badRequest().body(e.getMessage());
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body("An unexpected error occurred: " + e.getMessage());
        }
    }
}
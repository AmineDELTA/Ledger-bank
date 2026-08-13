package io.github.aminedelta.core_banking.controller;

import io.github.aminedelta.core_banking.domain.IdempotentRequest;
import io.github.aminedelta.core_banking.dto.TransferRequest;
import io.github.aminedelta.core_banking.exception.InsufficientFundsException;
import io.github.aminedelta.core_banking.repository.IdempotentRequestRepository;
import io.github.aminedelta.core_banking.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransferController {
    
    private final TransferService transferService;
    private final IdempotentRequestRepository idempotencyRepository;

    @PostMapping
    public ResponseEntity<String> executeTransfer(
            @RequestBody TransferRequest request, 
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        //intercept duplicate requests immediately using database constraints
        if (idempotencyKey != null) {
            try {
                //save an empty pending record. This locks the key across all concurrent threads
                IdempotentRequest initialRequest = new IdempotentRequest(idempotencyKey, null, null);
                idempotencyRepository.saveAndFlush(initialRequest);
            } catch (DataIntegrityViolationException e) {
                //another thread already saved this key
                Optional<IdempotentRequest> existingRequest = idempotencyRepository.findById(idempotencyKey);
                
                if (existingRequest.isPresent() && existingRequest.get().getResponseStatusCode() != null) {
                    //the request finished. Return the exact same response
                    return ResponseEntity
                            .status(existingRequest.get().getResponseStatusCode())
                            .body(existingRequest.get().getResponseBody());
                }
                //original request is still processing right now
                return ResponseEntity.status(409).body("Request is currently being processed.");
            }
        }

        ResponseEntity<String> response;

        try {
            transferService.transfer(
                request.getFromAccountId(),
                request.getToAccountId(),
                request.getAmount(),
                request.getDescription()
            );
            response = ResponseEntity.ok("Transfer completed successfully");
        } catch (InsufficientFundsException | IllegalArgumentException e) {
            response = ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            response = ResponseEntity.status(500).body("An unexpected error occurred: " + e.getMessage());
        }

        //save final state so future retries get the identical result
        if (idempotencyKey != null) {
            IdempotentRequest completedRequest = new IdempotentRequest(
                    idempotencyKey, 
                    response.getStatusCode().value(), 
                    response.getBody()
            );
            idempotencyRepository.save(completedRequest);
        }

        return response;
    }
}
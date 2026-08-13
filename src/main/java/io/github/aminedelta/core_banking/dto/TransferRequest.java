package io.github.aminedelta.core_banking.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

@Getter

public class TransferRequest {
    private UUID fromAccountId;
    private UUID toAccountId;
    private BigDecimal amount;
    private String description;
    
    // Protected no-arg constructor allows Jackson (JSON parser) to instantiate the object via reflection
    protected TransferRequest() {}

    public TransferRequest(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String description) {
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.description = description;
    }
}
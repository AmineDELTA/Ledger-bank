package io.github.aminedelta.core_banking.dto;

import io.github.aminedelta.core_banking.domain.EntryType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionHistoryResponse(
    UUID transactionId,
    LocalDateTime timestamp,
    String description,
    EntryType type,
    BigDecimal amount
) {
}

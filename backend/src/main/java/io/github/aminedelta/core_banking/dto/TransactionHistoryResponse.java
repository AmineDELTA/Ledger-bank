package io.github.aminedelta.core_banking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.aminedelta.core_banking.domain.EntryType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionHistoryResponse(
    UUID ledgerEntryId,
    UUID transactionId,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime timestamp,
    String description,
    EntryType type,
    BigDecimal amount
) {
}

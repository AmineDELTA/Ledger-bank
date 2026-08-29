package io.github.aminedelta.core_banking.dto;

import java.util.UUID;

public record TransferResult(
    UUID transactionId,
    String status,
    String message
) {}

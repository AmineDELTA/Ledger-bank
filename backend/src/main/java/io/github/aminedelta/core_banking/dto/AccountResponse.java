package io.github.aminedelta.core_banking.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AccountResponse {
    private UUID accountId;
    private String accountNumber;
    private String holderName;
    private BigDecimal balance;
}
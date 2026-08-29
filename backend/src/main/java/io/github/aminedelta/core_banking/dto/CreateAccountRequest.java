package io.github.aminedelta.core_banking.dto;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAccountRequest {
    private String accountNumber;
    private String holderName;
    private BigDecimal initialBalance;

    protected CreateAccountRequest() {}

    public CreateAccountRequest(String accountNumber, String holderName, BigDecimal initialBalance) {
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.initialBalance = initialBalance;
    }
}
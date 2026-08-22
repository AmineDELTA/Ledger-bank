package io.github.aminedelta.core_banking.dto;

import lombok.Getter;

@Getter
public class AccountRequest {
    private String accountNumber;
    private String holderName;

    protected AccountRequest() {}

    public AccountRequest(String accountNumber, String holderName) {
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        
    }
}
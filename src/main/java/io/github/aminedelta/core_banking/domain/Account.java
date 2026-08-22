package io.github.aminedelta.core_banking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;
import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String accountNumber;

    @Column(nullable = false)
    private String holderName;

    @Column(nullable = false)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    protected Account() {
    }

    public Account(String accountNumber, String holderName) {
        this.accountNumber = accountNumber;
        this.holderName = holderName;
    }

    public BigDecimal getBalance() {
        return currentBalance;
    }

    public void setBalance(BigDecimal balance) {
        this.currentBalance = balance;
    }
}
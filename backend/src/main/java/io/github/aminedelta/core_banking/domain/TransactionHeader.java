package io.github.aminedelta.core_banking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "transaction_headers")
public class TransactionHeader {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    private String description;

    protected TransactionHeader() {
    }

    public TransactionHeader(String description) {
        this.timestamp = LocalDateTime.now();
        this.description = description;
    }
}
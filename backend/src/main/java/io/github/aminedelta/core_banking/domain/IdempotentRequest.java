package io.github.aminedelta.core_banking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class IdempotentRequest {

    @Id
    private String idempotency_key;

    // 1. Replaced 'String request_code' with an Integer to store the HTTP status cleanly
    private Integer responseStatusCode;

    @Column(columnDefinition = "TEXT")
    private String responseBody;

    // 2. JPA/Hibernate always requires a default empty constructor
    public IdempotentRequest() {
    }

    // 3. Fixed the constructor arguments and assignments
    public IdempotentRequest(String idempotency_key, Integer responseStatusCode, String responseBody) {
        this.idempotency_key = idempotency_key;
        this.responseStatusCode = responseStatusCode;
        this.responseBody = responseBody;
    }

    public String getIdempotency_key() {
        return idempotency_key;
    }

    // 4. No more string parsing. Just return the integer.
    public Integer getResponseStatusCode() {
        return responseStatusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
package io.github.aminedelta.core_banking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class IdempotentRequest {

    @Id
    private String idempotency_key;

    private Integer responseStatusCode;

    @Column(columnDefinition = "TEXT")
    private String responseBody;

    public IdempotentRequest() {
    }

    public IdempotentRequest(String idempotency_key, Integer responseStatusCode, String responseBody) {
        this.idempotency_key = idempotency_key;
        this.responseStatusCode = responseStatusCode;
        this.responseBody = responseBody;
    }

    public String getIdempotency_key() {
        return idempotency_key;
    }

    public Integer getResponseStatusCode() {
        return responseStatusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
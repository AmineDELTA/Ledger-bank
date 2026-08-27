package io.github.aminedelta.core_banking.dto;

import lombok.Getter;

@Getter
public class ErrorResponse {
    private String message;
    private String error; 
    private String timestamp;
    private int status;

    protected ErrorResponse() {}
   
    public ErrorResponse(String message, String error, String timestamp, int status) {
        this.message = message;
        this.error = error;
        this.timestamp = timestamp;
        this.status = status;
    }
}

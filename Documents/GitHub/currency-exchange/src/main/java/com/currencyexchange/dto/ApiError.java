package com.currencyexchange.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApiError {
    private int status;
    private String message;
    private LocalDateTime timestamp;

    public static ApiError of(int status, String message) {
        ApiError e = new ApiError();
        e.status    = status;
        e.message   = message;
        e.timestamp = LocalDateTime.now();
        return e;
    }
}
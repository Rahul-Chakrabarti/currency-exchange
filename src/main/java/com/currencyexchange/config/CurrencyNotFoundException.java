package com.currencyexchange.config;

public class CurrencyNotFoundException extends RuntimeException {
    public CurrencyNotFoundException(String message) {
        super("Currency not found or not supported: " + message);
    }
}
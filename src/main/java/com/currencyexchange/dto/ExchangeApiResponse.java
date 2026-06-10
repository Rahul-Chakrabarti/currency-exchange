package com.currencyexchange.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class ExchangeApiResponse {

    @JsonProperty("result")
    private String result;

    @JsonProperty("base_code")
    private String baseCode;

    @JsonProperty("rates")
    private Map<String, BigDecimal> rates;

    @JsonProperty("error-type")
    private String errorType;

    public boolean isSuccess() {
        return "success".equalsIgnoreCase(result);
    }
}
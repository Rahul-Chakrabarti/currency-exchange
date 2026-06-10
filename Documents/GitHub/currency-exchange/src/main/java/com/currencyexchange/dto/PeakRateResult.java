package com.currencyexchange.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PeakRateResult {

    private String baseCurrency;
    private String targetCurrency;

    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;

    private BigDecimal peakRate;
    private LocalDateTime peakAt;

    private BigDecimal currentRate;
    private BigDecimal changeFromPeak;
    private Double     changePercent;
    private String     insight;
}
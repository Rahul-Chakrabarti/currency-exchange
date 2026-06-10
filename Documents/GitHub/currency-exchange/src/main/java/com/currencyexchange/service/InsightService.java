package com.currencyexchange.service;

import com.currencyexchange.dto.PeakRateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightService {

    private final JsonExportService jsonExportService;
    private final ExchangeRateService exchangeRateService;

    public PeakRateResult analyse(String filename) throws IOException {
        PeakRateResult result = jsonExportService.readPeak(filename);

        BigDecimal current = exchangeRateService.getLatestRate(
                result.getBaseCurrency(), result.getTargetCurrency());
        result.setCurrentRate(current);

        BigDecimal change = current.subtract(result.getPeakRate())
                                   .setScale(6, RoundingMode.HALF_UP);
        result.setChangeFromPeak(change);

        if (result.getPeakRate().compareTo(BigDecimal.ZERO) != 0) {
            double pct = change.divide(result.getPeakRate(), MathContext.DECIMAL64)
                               .multiply(BigDecimal.valueOf(100))
                               .setScale(4, RoundingMode.HALF_UP)
                               .doubleValue();
            result.setChangePercent(pct);
        }

        result.setInsight(buildInsight(result));
        log.info("Insight for {}: {}", filename, result.getInsight());
        return result;
    }

    private String buildInsight(PeakRateResult r) {
        String pair   = r.getBaseCurrency() + "/" + r.getTargetCurrency();
        String peak   = r.getPeakRate().toPlainString();
        String now    = r.getCurrentRate().toPlainString();
        double pct    = r.getChangePercent() != null ? r.getChangePercent() : 0.0;
        String pctStr = String.format("%.2f%%", Math.abs(pct));

        if (pct > 0) {
            return String.format(
                "%s peaked at %s on %s. The current rate is %s, up %s from the peak.",
                pair, peak, r.getPeakAt().toLocalDate(), now, pctStr);
        } else if (pct < 0) {
            return String.format(
                "%s peaked at %s on %s. The current rate is %s, down %s from the peak.",
                pair, peak, r.getPeakAt().toLocalDate(), now, pctStr);
        } else {
            return String.format(
                "%s peaked at %s on %s. The current rate is unchanged at %s.",
                pair, peak, r.getPeakAt().toLocalDate(), now);
        }
    }
}
package com.currencyexchange.service;

import com.currencyexchange.config.CurrencyNotFoundException;
import com.currencyexchange.dto.ExchangeApiResponse;
import com.currencyexchange.dto.PeakRateResult;
import com.currencyexchange.dto.RateSnapshot;
import com.currencyexchange.model.ExchangeRate;
import com.currencyexchange.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository repo;
    private final RestTemplate restTemplate;

    @Value("${exchange.api.base-url}")
    private String apiBaseUrl;

    @Value("${exchange.api.default-base}")
    private String defaultBase;

    public void fetchAndSaveRates() {
        fetchAndSaveRatesForBase(defaultBase);
    }

    @Transactional
    public void fetchAndSaveRatesForBase(String baseCurrency) {
        String url = apiBaseUrl + "/" + baseCurrency.toUpperCase();
        log.info("Fetching rates from {}", url);

        ExchangeApiResponse response = restTemplate.getForObject(url, ExchangeApiResponse.class);

        if (response == null || !response.isSuccess()) {
            String error = response != null ? response.getErrorType() : "null response";
            throw new IllegalStateException("External API error: " + error);
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        String base = response.getBaseCode();

        List<ExchangeRate> toSave = response.getRates().entrySet().stream()
                .filter(e -> !e.getKey().equals(base))
                .filter(e -> !repo.existsByBaseCurrencyAndTargetCurrencyAndRecordedAt(
                        base, e.getKey(), now))
                .map(e -> ExchangeRate.builder()
                        .baseCurrency(base)
                        .targetCurrency(e.getKey())
                        .rate(e.getValue())
                        .recordedAt(now)
                        .build())
                .collect(Collectors.toList());

        repo.saveAll(toSave);
        log.info("Saved {} rate snapshots for base {}", toSave.size(), base);
    }

    public BigDecimal getLatestRate(String from, String to) {
        String f = from.toUpperCase();
        String t = to.toUpperCase();

        if (f.equals(t)) return BigDecimal.ONE;

        return repo.findTopByBaseCurrencyAndTargetCurrencyOrderByRecordedAtDesc(f, t)
                .map(ExchangeRate::getRate)
                .orElseThrow(() -> new CurrencyNotFoundException(
                        f + " → " + t + ". Try POST /api/rates/refresh first."));
    }

    public Map<String, BigDecimal> getLatestRatesForBase(String baseCurrency) {
        String base = baseCurrency.toUpperCase();
        List<ExchangeRate> rates = repo.findLatestRatesForBase(base);

        if (rates.isEmpty()) {
            throw new IllegalStateException(
                    "No rates stored for " + base + ". Try POST /api/rates/refresh.");
        }

        return rates.stream()
                .collect(Collectors.toMap(ExchangeRate::getTargetCurrency, ExchangeRate::getRate));
    }

    public List<RateSnapshot> getHistory(String from, String to,
                                         LocalDateTime start, LocalDateTime end) {
        return repo.findByBaseCurrencyAndTargetCurrencyAndRecordedAtBetweenOrderByRecordedAtAsc(
                        from.toUpperCase(), to.toUpperCase(), start, end)
                .stream()
                .map(er -> new RateSnapshot(er.getRate(), er.getRecordedAt()))
                .collect(Collectors.toList());
    }

    public PeakRateResult findPeak(String from, String to,
                                   LocalDateTime start, LocalDateTime end) {
        String f = from.toUpperCase();
        String t = to.toUpperCase();

        ExchangeRate peak = repo.findPeakRate(f, t, start, end)
                .orElseThrow(() -> new CurrencyNotFoundException(
                        "No data for " + f + " → " + t + " in the requested window."));

        PeakRateResult result = new PeakRateResult();
        result.setBaseCurrency(f);
        result.setTargetCurrency(t);
        result.setWindowStart(start);
        result.setWindowEnd(end);
        result.setPeakRate(peak.getRate());
        result.setPeakAt(peak.getRecordedAt());
        return result;
    }
}
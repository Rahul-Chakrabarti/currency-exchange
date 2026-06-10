package com.currencyexchange.controller;

import com.currencyexchange.dto.PeakRateResult;
import com.currencyexchange.dto.RateSnapshot;
import com.currencyexchange.service.ExchangeRateService;
import com.currencyexchange.service.InsightService;
import com.currencyexchange.service.JsonExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rates")
@RequiredArgsConstructor
public class RateController {

    private final ExchangeRateService exchangeRateService;
    private final JsonExportService   jsonExportService;
    private final InsightService      insightService;

    // GET /api/rates/latest?base=USD
    @GetMapping("/latest")
    public ResponseEntity<Map<String, BigDecimal>> getLatestRates(
            @RequestParam(defaultValue = "USD") String base) {
        return ResponseEntity.ok(exchangeRateService.getLatestRatesForBase(base));
    }

    // GET /api/rates/latest/pair?from=USD&to=EUR
    @GetMapping("/latest/pair")
    public ResponseEntity<Map<String, Object>> getLatestPair(
            @RequestParam String from,
            @RequestParam String to) {
        BigDecimal rate = exchangeRateService.getLatestRate(from, to);
        return ResponseEntity.ok(Map.of(
                "from", from.toUpperCase(),
                "to",   to.toUpperCase(),
                "rate", rate
        ));
    }

    // GET /api/rates/history?from=USD&to=EUR&start=...&end=...
    @GetMapping("/history")
    public ResponseEntity<List<RateSnapshot>> getHistory(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("'start' must be strictly before 'end'");
        }
        return ResponseEntity.ok(exchangeRateService.getHistory(from, to, start, end));
    }

    // GET /api/rates/peak?from=USD&to=EUR&start=...&end=...
    @GetMapping("/peak")
    public ResponseEntity<Map<String, Object>> getPeak(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) throws IOException {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("'start' must be strictly before 'end'");
        }
        PeakRateResult peak = exchangeRateService.findPeak(from, to, start, end);
        Path exported = jsonExportService.writePeak(peak);
        return ResponseEntity.ok(Map.of(
                "peak",         peak,
                "exportedFile", exported.getFileName().toString()
        ));
    }

    // POST /api/rates/refresh?base=USD
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(
            @RequestParam(defaultValue = "USD") String base) {
        exchangeRateService.fetchAndSaveRatesForBase(base);
        return ResponseEntity.ok(Map.of(
                "status",  "ok",
                "message", "Rates refreshed for: " + base.toUpperCase()
        ));
    }

    // GET /api/rates/exports
    @GetMapping("/exports")
    public ResponseEntity<List<String>> listExports() throws IOException {
        return ResponseEntity.ok(jsonExportService.listExports());
    }

    // GET /api/rates/insights?file=peak_USD_EUR_20240601_120000.json
    @GetMapping("/insights")
    public ResponseEntity<PeakRateResult> getInsight(
            @RequestParam String file) throws IOException {
        return ResponseEntity.ok(insightService.analyse(file));
    }
}

package com.currencyexchange.controller;

import com.currencyexchange.dto.ForecastResult;
import com.currencyexchange.service.ForecastService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class ForecastController {

    private final ForecastService forecastService;

    // GET /api/forecast?from=CAD&to=INR
    @GetMapping("/api/forecast")
    public ResponseEntity<ForecastResult> getForecast(
            @RequestParam(defaultValue = "CAD") String from,
            @RequestParam(defaultValue = "INR") String to)
            throws IOException, InterruptedException {
        return ResponseEntity.ok(forecastService.forecast(from, to));
    }
}

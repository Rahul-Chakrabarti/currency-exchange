package com.currencyexchange.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Deserialisation target for the JSON output written by {@code ml/forecast.py}.
 *
 * <p>{@link com.currencyexchange.service.ForecastService} runs the Python script
 * as a subprocess, waits for it to finish, then reads the JSON file it produced
 * into this object. The object is returned directly as the response body for
 * {@code GET /api/forecast}.
 *
 * <p>Example JSON produced by the Python script:
 * <pre>{@code
 * {
 *   "baseCurrency":     "CAD",
 *   "targetCurrency":   "INR",
 *   "currentRate":      61.847293,
 *   "generatedAt":      "2025-05-09T22:00:00",
 *   "recommendation":   "WAIT",
 *   "confidence":       73.5,
 *   "reasoning":        "The rate is forecast to rise...",
 *   "tomorrowForecast": 62.103847,
 *   "sevenDayForecast": [
 *     { "date": "2025-05-10", "predicted_rate": 62.103847 },
 *     ...
 *   ],
 *   "models": {
 *     "linearRegression": { "tomorrowPrediction": 62.1, "trend": "UP" },
 *     "randomForest":     { "probabilityUp": 68.2, "probabilityDown": 31.8, "vote": "UP" },
 *     "arima":            { "sevenDayTrend": "UP", "projectedRate": 62.8 }
 *   }
 * }
 * }</pre>
 */
@Data
public class ForecastResult {

    /** The currency rates are expressed in terms of, e.g. {@code "CAD"}. */
    private String baseCurrency;

    /** The currency being priced, e.g. {@code "INR"}. */
    private String targetCurrency;

    /** Live rate at the time the Python script ran. */
    private Double currentRate;

    /** ISO-8601 timestamp of when the forecast was generated. */
    private String generatedAt;

    /**
     * The combined model recommendation.
     * One of: {@code "BUY NOW"}, {@code "WAIT"}, {@code "HOLD"}.
     */
    private String recommendation;

    /**
     * Composite confidence score from 0 to 100.
     * Combines Random Forest directional probability and ARIMA signal strength.
     */
    private Double confidence;

    /** Plain-English explanation of the recommendation and the signals behind it. */
    private String reasoning;

    /** Linear Regression's predicted rate for tomorrow. */
    private Double tomorrowForecast;

    /**
     * ARIMA 7-day forecast as a list of date/rate pairs.
     * Each entry: {@code {"date": "2025-05-10", "predicted_rate": 62.1}}.
     * Typed as {@code Map<String, Object>} to match the Python output directly
     * without needing a dedicated inner class.
     */
    private List<Map<String, Object>> sevenDayForecast;

    /**
     * Individual model outputs for display in the web interface.
     * Keys: {@code "linearRegression"}, {@code "randomForest"}, {@code "arima"}.
     * Each value is a map of model-specific metrics.
     */
    private Map<String, Object> models;
}

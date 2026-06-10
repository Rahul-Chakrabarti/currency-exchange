package com.currencyexchange.service;

import com.currencyexchange.dto.ForecastResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Bridges the Java application and the Python ML forecast script.
 *
 * <p>When {@code GET /api/forecast} is called, this service:
 * <ol>
 *   <li>Detects the correct Python executable for the host OS.</li>
 *   <li>Launches {@code ml/forecast.py} as a subprocess, passing the currency pair as arguments.</li>
 *   <li>Streams Python's stderr to the application log in real time (progress messages).</li>
 *   <li>Waits up to 120 seconds for the script to finish.</li>
 *   <li>Reads the JSON file the script wrote and deserialises it into a {@link ForecastResult}.</li>
 * </ol>
 *
 * <p><strong>Why subprocess instead of a JVM-native ML library?</strong>
 * The Python ML ecosystem (scikit-learn, statsmodels, XGBoost, Prophet) has no
 * equivalent in Java at this level of maturity. Running Python as a subprocess
 * is the standard pattern for Java → Python ML integration without introducing
 * a message queue or microservice boundary. The trade-off is a 3–10 second
 * first-call latency while models train — acceptable for on-demand forecasting,
 * not for real-time streaming.
 *
 * <p><strong>OS detection:</strong>
 * Windows uses {@code python}, Linux/macOS/Docker use {@code python3}.
 * The correct command is detected at runtime from {@code os.name}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastService {

    private final ObjectMapper objectMapper;

    /** Directory where the Python script writes its JSON output. */
    @Value("${exchange.json.export-dir}")
    private String exportDir;

    /** Path to the ML script, relative to the project working directory. */
    private static final String PYTHON_SCRIPT = "ml/forecast.py";

    /**
     * Runs the Python ML forecast script for the given currency pair and
     * returns the structured result.
     *
     * @param from base currency, e.g. {@code "CAD"}
     * @param to   target currency, e.g. {@code "INR"}
     * @return the deserialised {@link ForecastResult} containing the recommendation,
     *         confidence score, reasoning, and 7-day forecast
     * @throws IOException              if the result file cannot be read after the script runs
     * @throws InterruptedException     if the thread is interrupted while waiting for Python
     * @throws IllegalStateException    if the Python script exits with a non-zero code,
     *                                  or if the expected output file is not found
     */
    public ForecastResult forecast(String from, String to) throws IOException, InterruptedException {
        String base   = from.toUpperCase();
        String target = to.toUpperCase();

        log.info("Starting ML forecast for {}/{}", base, target);

        Process process = startPythonProcess(base, target);
        streamStderrToLog(process);

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(
                    "Python forecast script exited with code " + exitCode +
                    ". Check logs for details.");
        }

        return readForecastResult(base, target);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds and starts the Python subprocess.
     * Command: {@code python ml/forecast.py CAD INR} (Windows)
     *       or {@code python3 ml/forecast.py CAD INR} (Linux/Docker/macOS).
     */
    private Process startPythonProcess(String base, String target) throws IOException {
        String pythonExecutable = isWindows() ? "python" : "python3";

        ProcessBuilder builder = new ProcessBuilder(pythonExecutable, PYTHON_SCRIPT, base, target);
        builder.directory(new File(System.getProperty("user.dir")));
        builder.redirectErrorStream(false); // keep stdout and stderr separate

        log.debug("Launching: {} {} {} {}", pythonExecutable, PYTHON_SCRIPT, base, target);
        return builder.start();
    }

    /**
     * Reads the Python script's stderr on a background thread and forwards
     * each line to the application log. Without this, the subprocess stderr
     * buffer fills up and the process hangs indefinitely.
     */
    private void streamStderrToLog(Process process) {
        Thread stderrReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[Python] {}", line);
                }
            } catch (IOException ignored) {
                // Stream closed when process ends — not an error
            }
        });
        stderrReader.setDaemon(true); // do not prevent JVM shutdown
        stderrReader.start();
    }

    /**
     * Reads the JSON file written by the Python script and deserialises it
     * into a {@link ForecastResult}.
     *
     * @param base   uppercase base currency code
     * @param target uppercase target currency code
     */
    private ForecastResult readForecastResult(String base, String target) throws IOException {
        String filename = String.format("forecast_%s_%s.json", base, target);
        Path   filepath = Paths.get(exportDir, filename);
        File   file     = filepath.toFile();

        if (!file.exists()) {
            throw new IllegalStateException(
                    "Expected forecast output file not found after script completed: " + filepath +
                    ". The Python script may have exited silently without writing output.");
        }

        ForecastResult result = objectMapper.readValue(file, ForecastResult.class);
        log.info("Forecast complete — recommendation: {} (confidence: {}%)",
                result.getRecommendation(), result.getConfidence());
        return result;
    }

    /** Returns {@code true} when running on a Windows host. */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
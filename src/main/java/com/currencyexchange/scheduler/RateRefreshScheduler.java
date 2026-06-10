package com.currencyexchange.scheduler;

import com.currencyexchange.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Automatically fetches and persists fresh exchange rates on a schedule.
 *
 * <p>There are two triggers:
 * <ol>
 *   <li><strong>Startup trigger</strong> ({@code @EventListener(ApplicationReadyEvent.class)}) —
 *       fires once immediately after the application fully starts. Retries up to
 *       {@value MAX_STARTUP_RETRIES} times with a {@value RETRY_DELAY_MS}ms delay between
 *       attempts so that a brief network blip or late-starting database does not
 *       leave the app with an empty rate table.</li>
 *   <li><strong>Cron trigger</strong> ({@code @Scheduled}) — fires every 6 hours to keep
 *       rates current throughout the day.</li>
 * </ol>
 *
 * <p><strong>Why retry on startup?</strong>
 * Inside Docker, even though {@code depends_on: service_healthy} ensures PostgreSQL
 * is accepting connections before the app starts, the HikariCP connection pool may
 * take a moment to establish its first connection. The retry loop absorbs this
 * window without requiring complex Docker health-check tuning.
 *
 * <p><strong>Why {@code fixedDelay} instead of cron for the schedule?</strong>
 * {@code fixedDelay} fires N milliseconds after the previous execution completes,
 * meaning the 6-hour clock resets after each successful fetch. This is actually
 * better than cron for this use case — if a fetch takes a few seconds, the next
 * one still runs 6 hours later rather than potentially overlapping.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateRefreshScheduler {

    private final ExchangeRateService exchangeRateService;

    // Maximum number of startup fetch attempts before giving up
    private static final int MAX_STARTUP_RETRIES = 5;

    // Milliseconds to wait between startup retry attempts (10 seconds)
    private static final long RETRY_DELAY_MS = 10_000;

    /**
     * Fetches rates immediately after startup with automatic retry.
     *
     * <p>Attempts up to {@value MAX_STARTUP_RETRIES} times, waiting
     * {@value RETRY_DELAY_MS}ms between each attempt. If all attempts fail
     * (e.g. no internet connection), the app continues running and rates will
     * be fetched on the next scheduled trigger.
     *
     * <p>Uses {@code ApplicationReadyEvent} rather than {@code @PostConstruct}
     * because the event fires after the full Spring context — including the
     * database connection pool — is completely ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void fetchRatesOnStartup() {
        log.info("Application ready — fetching initial exchange rates (up to {} attempts)...",
                MAX_STARTUP_RETRIES);

        for (int attempt = 1; attempt <= MAX_STARTUP_RETRIES; attempt++) {
            try {
                exchangeRateService.fetchAndSaveRates();
                log.info("Startup rate fetch succeeded on attempt {}/{}", attempt, MAX_STARTUP_RETRIES);
                return; // success — exit immediately
            } catch (Exception ex) {
                if (attempt < MAX_STARTUP_RETRIES) {
                    log.warn("Startup rate fetch attempt {}/{} failed: {}. Retrying in {}s...",
                            attempt, MAX_STARTUP_RETRIES, ex.getMessage(), RETRY_DELAY_MS / 1000);
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Startup retry interrupted — proceeding without initial rates");
                        return;
                    }
                } else {
                    log.error("All {} startup fetch attempts failed. " +
                              "Rates will be fetched on the next scheduled run (in 6 hours). " +
                              "Last error: {}", MAX_STARTUP_RETRIES, ex.getMessage());
                }
            }
        }
    }

    /**
     * Refreshes rates every 6 hours automatically.
     *
     * {@code fixedDelay = 21_600_000} = 6 hours in milliseconds.
     * The delay starts after the previous execution completes, so fetches
     * never overlap even if one takes longer than expected.
     *
     * {@code initialDelay = 21_600_000} means the first scheduled run
     * fires 6 hours after startup — the startup fetch above handles the
     * immediate population so there is no need to run twice at launch.
     *
     * Failures are logged at ERROR level and the scheduler continues —
     * the app serves the last successfully fetched rates until the next run.
     */
    @Scheduled(fixedDelay = 21_600_000, initialDelay = 21_600_000)
    public void fetchRatesOnSchedule() {
        log.info("Scheduled 6-hour rate refresh triggered");
        try {
            exchangeRateService.fetchAndSaveRates();
        } catch (Exception ex) {
            log.error("Scheduled rate refresh failed. Rates will remain at last known values " +
                      "until the next run in 6 hours. Reason: {}", ex.getMessage());
        }
    }
}
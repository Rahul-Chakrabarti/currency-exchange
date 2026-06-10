package com.currencyexchange.repository;

import com.currencyexchange.model.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findTopByBaseCurrencyAndTargetCurrencyOrderByRecordedAtDesc(
            String baseCurrency, String targetCurrency);

    List<ExchangeRate> findByBaseCurrencyAndTargetCurrencyAndRecordedAtBetweenOrderByRecordedAtAsc(
            String baseCurrency, String targetCurrency,
            LocalDateTime from, LocalDateTime to);

    @Query("""
            SELECT er FROM ExchangeRate er
            WHERE er.baseCurrency   = :base
              AND er.targetCurrency = :target
              AND er.recordedAt BETWEEN :from AND :to
              AND er.rate = (
                  SELECT MAX(er2.rate)
                  FROM ExchangeRate er2
                  WHERE er2.baseCurrency   = :base
                    AND er2.targetCurrency = :target
                    AND er2.recordedAt BETWEEN :from AND :to
              )
            """)
    Optional<ExchangeRate> findPeakRate(
            @Param("base")   String baseCurrency,
            @Param("target") String targetCurrency,
            @Param("from")   LocalDateTime from,
            @Param("to")     LocalDateTime to);

    @Query("""
            SELECT er FROM ExchangeRate er
            WHERE er.baseCurrency = :base
              AND er.recordedAt = (
                  SELECT MAX(er2.recordedAt)
                  FROM ExchangeRate er2
                  WHERE er2.baseCurrency   = er.baseCurrency
                    AND er2.targetCurrency = er.targetCurrency
              )
            """)
    List<ExchangeRate> findLatestRatesForBase(@Param("base") String baseCurrency);

    boolean existsByBaseCurrencyAndTargetCurrencyAndRecordedAt(
            String baseCurrency, String targetCurrency, LocalDateTime recordedAt);
}
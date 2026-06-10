package com.currencyexchange.service;

import com.currencyexchange.dto.PeakRateResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class JsonExportService {

    private final ObjectMapper objectMapper;
    private final Path exportDir;

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public Path writePeak(PeakRateResult result) throws IOException {
        String filename = String.format("peak_%s_%s_%s.json",
                result.getBaseCurrency(),
                result.getTargetCurrency(),
                result.getPeakAt().format(FILE_TS));

        Path file = exportDir.resolve(filename);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), result);
        log.info("Peak written to {}", file);
        return file;
    }

    public PeakRateResult readPeak(String filename) throws IOException {
        Path file = exportDir.resolve(filename);
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("Export file not found: " + filename);
        }
        return objectMapper.readValue(file.toFile(), PeakRateResult.class);
    }

    public List<String> listExports() throws IOException {
        List<String> names = new ArrayList<>();
        try (Stream<Path> stream = Files.list(exportDir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                  .sorted(Comparator.reverseOrder())
                  .map(p -> p.getFileName().toString())
                  .forEach(names::add);
        }
        return names;
    }
}
package com.mocktalkback.domain.content.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.mocktalkback.domain.content.dto.AdminMarketImportFailureResponse;
import com.mocktalkback.domain.content.type.MarketInstrumentCode;

@Service
public class MarketSnapshotImportService {

    public MarketSnapshotImportParsedResult parse(MultipartFile file, MarketInstrumentCode selectedInstrument) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            if (filename.endsWith(".csv")) {
                return parseCsv(file.getInputStream(), selectedInstrument);
            }
            if (filename.endsWith(".xlsx")) {
                return parseExcel(file.getInputStream(), selectedInstrument);
            }
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.MARKET_IMPORT_READ_FAILED);
        }

        throw new ApiException(ErrorCode.MARKET_IMPORT_FORMAT_UNSUPPORTED);
    }

    private MarketSnapshotImportParsedResult parseCsv(InputStream inputStream, MarketInstrumentCode selectedInstrument) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            List<MarketSnapshotImportRow> rows = new ArrayList<>();
            List<AdminMarketImportFailureResponse> failures = new ArrayList<>();
            String headerLine = readNextNonEmptyLine(reader);
            if (headerLine == null) {
                throw new ApiException(ErrorCode.MARKET_IMPORT_CSV_EMPTY);
            }
            Map<String, Integer> headerMap = createHeaderMap(parseCsvLine(headerLine));
            validateHeaders(headerMap, selectedInstrument == null);

            String line;
            int rowNumber = 1;
            int totalCount = 0;
            while ((line = reader.readLine()) != null) {
                rowNumber += 1;
                if (line.isBlank()) {
                    continue;
                }
                totalCount += 1;
                try {
                    List<String> values = parseCsvLine(line);
                    rows.add(createImportRow(headerMap, values, rowNumber, selectedInstrument));
                } catch (IllegalArgumentException ex) {
                    failures.add(new AdminMarketImportFailureResponse(rowNumber, ex.getMessage()));
                }
            }
            return new MarketSnapshotImportParsedResult(totalCount, rows, failures);
        }
    }

    private MarketSnapshotImportParsedResult parseExcel(InputStream inputStream, MarketInstrumentCode selectedInstrument) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) {
                throw new ApiException(ErrorCode.MARKET_IMPORT_XLSX_EMPTY);
            }

            DataFormatter formatter = new DataFormatter();
            Row headerRow = findFirstNonEmptyRow(sheet);
            if (headerRow == null) {
                throw new ApiException(ErrorCode.MARKET_IMPORT_XLSX_EMPTY);
            }

            Map<String, Integer> headerMap = createHeaderMap(readRowValues(headerRow, formatter));
            validateHeaders(headerMap, selectedInstrument == null);

            List<MarketSnapshotImportRow> rows = new ArrayList<>();
            List<AdminMarketImportFailureResponse> failures = new ArrayList<>();
            int totalCount = 0;

            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isRowBlank(row, formatter)) {
                    continue;
                }

                totalCount += 1;
                List<String> values = readRowValues(row, formatter);
                try {
                    rows.add(createImportRow(headerMap, values, rowIndex + 1, selectedInstrument));
                } catch (IllegalArgumentException ex) {
                    failures.add(new AdminMarketImportFailureResponse(rowIndex + 1, ex.getMessage()));
                }
            }

            return new MarketSnapshotImportParsedResult(totalCount, rows, failures);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.MARKET_IMPORT_XLSX_PARSE_FAILED);
        }
    }

    private Row findFirstNonEmptyRow(Sheet sheet) {
        DataFormatter formatter = new DataFormatter();
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null && !isRowBlank(row, formatter)) {
                return row;
            }
        }
        return null;
    }

    private boolean isRowBlank(Row row, DataFormatter formatter) {
        for (int cellIndex = row.getFirstCellNum(); cellIndex < row.getLastCellNum(); cellIndex++) {
            if (cellIndex < 0) {
                continue;
            }
            Cell cell = row.getCell(cellIndex);
            if (cell != null && !formatter.formatCellValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private List<String> readRowValues(Row row, DataFormatter formatter) {
        List<String> values = new ArrayList<>();
        for (int cellIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++) {
            Cell cell = row.getCell(cellIndex);
            values.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
        }
        return values;
    }

    private String readNextNonEmptyLine(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) {
                return stripBom(line);
            }
        }
        return null;
    }

    private String stripBom(String value) {
        if (!value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    private Map<String, Integer> createHeaderMap(List<String> headers) {
        Map<String, Integer> headerMap = new HashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            String normalized = normalizeHeader(headers.get(index));
            if (!normalized.isEmpty()) {
                headerMap.put(normalized, index);
            }
        }
        return headerMap;
    }

    private void validateHeaders(Map<String, Integer> headerMap, boolean unifiedFile) {
        if (unifiedFile && !headerMap.containsKey("instrument_code")) {
            throw new ApiException(ErrorCode.MARKET_INSTRUMENT_CODE_REQUIRED);
        }
        if (!headerMap.containsKey("observed_at")) {
            throw new ApiException(ErrorCode.MARKET_OBSERVED_AT_REQUIRED);
        }
        if (!headerMap.containsKey("price_value")) {
            throw new ApiException(ErrorCode.MARKET_PRICE_VALUE_REQUIRED);
        }
    }

    private MarketSnapshotImportRow createImportRow(
        Map<String, Integer> headerMap,
        List<String> values,
        int rowNumber,
        MarketInstrumentCode selectedInstrument
    ) {
        MarketInstrumentCode instrumentCode = selectedInstrument;
        String instrumentValue = readValue(headerMap, values, "instrument_code");
        if (selectedInstrument == null) {
            instrumentCode = parseInstrumentCode(instrumentValue);
        } else if (!instrumentValue.isBlank()) {
            MarketInstrumentCode rowInstrumentCode = parseInstrumentCode(instrumentValue);
            if (rowInstrumentCode != selectedInstrument) {
                throw new ApiException(ErrorCode.MARKET_INSTRUMENT_MISMATCH);
            }
        }

        if (instrumentCode == null) {
            throw new ApiException(ErrorCode.MARKET_INSTRUMENT_UNKNOWN);
        }

        String observedAtValue = readValue(headerMap, values, "observed_at");
        String priceValue = readValue(headerMap, values, "price_value");

        return new MarketSnapshotImportRow(
            rowNumber,
            instrumentCode,
            parseObservedAt(observedAtValue),
            parsePriceValue(priceValue)
        );
    }

    private String readValue(Map<String, Integer> headerMap, List<String> values, String key) {
        Integer index = headerMap.get(key);
        if (index == null || index >= values.size()) {
            return "";
        }
        return values.get(index).trim();
    }

    private MarketInstrumentCode parseInstrumentCode(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new ApiException(ErrorCode.MARKET_INSTRUMENT_CODE_EMPTY);
        }
        try {
            return MarketInstrumentCode.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.MARKET_INSTRUMENT_UNSUPPORTED, rawValue);
        }
    }

    private Instant parseObservedAt(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new ApiException(ErrorCode.MARKET_OBSERVED_AT_EMPTY);
        }
        try {
            if (rawValue.length() == 10) {
                return LocalDate.parse(rawValue).atStartOfDay(ZoneOffset.UTC).toInstant();
            }
            return Instant.parse(rawValue);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(rawValue).atZone(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException ignored) {
                throw new ApiException(ErrorCode.MARKET_OBSERVED_AT_INVALID, rawValue);
            }
        }
    }

    private BigDecimal parsePriceValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new ApiException(ErrorCode.MARKET_PRICE_EMPTY);
        }
        try {
            return new BigDecimal(rawValue.trim());
        } catch (NumberFormatException ex) {
            throw new ApiException(ErrorCode.MARKET_PRICE_INVALID, rawValue);
        }
    }

    private String normalizeHeader(String value) {
        return unquote(value)
            .trim()
            .toLowerCase(Locale.ROOT);
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int index = 0; index < line.length(); index++) {
            char currentChar = line.charAt(index);
            if (currentChar == '"') {
                if (inQuotes && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index += 1;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (currentChar == ',' && !inQuotes) {
                values.add(unquote(current.toString()).trim());
                current.setLength(0);
                continue;
            }
            current.append(currentChar);
        }
        values.add(unquote(current.toString()).trim());
        return values;
    }

    private String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}

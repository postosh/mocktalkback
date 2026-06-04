package com.mocktalkback.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.time.Instant;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.mocktalkback.domain.content.type.MarketInstrumentCode;
import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;

class MarketSnapshotImportServiceTest {

    // 통합 CSV 임포트는 instrument_code 컬럼을 읽어 row를 만들어야 한다.
    @Test
    void parse_reads_unified_csv_rows() {
        // Given: 통합 CSV 파일이 준비되어 있다.
        MarketSnapshotImportService service = new MarketSnapshotImportService();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "market.csv",
            "text/csv",
            "instrument_code,observed_at,price_value\nUSD_KRW,2026-03-15,1450.12".getBytes()
        );

        // When: 통합 CSV를 파싱하면
        MarketSnapshotImportParsedResult result = service.parse(file, null);

        // Then: row와 값이 정상 해석되어야 한다.
        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.failures()).isEmpty();
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).instrumentCode()).isEqualTo(MarketInstrumentCode.USD_KRW);
        assertThat(result.rows().get(0).priceValue()).isEqualByComparingTo("1450.12");
    }

    // 종목별 CSV 임포트는 instrument_code 없이도 선택한 종목으로 row를 만들어야 한다.
    @Test
    void parse_reads_single_instrument_csv_rows() {
        // Given: 종목별 CSV 파일과 선택 종목이 준비되어 있다.
        MarketSnapshotImportService service = new MarketSnapshotImportService();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "usd.csv",
            "text/csv",
            "observed_at,price_value\n2026-03-15,1450.12".getBytes()
        );

        // When: 종목별 CSV를 파싱하면
        MarketSnapshotImportParsedResult result = service.parse(file, MarketInstrumentCode.USD_KRW);

        // Then: 선택 종목 기준 row가 생성되어야 한다.
        assertThat(result.failures()).isEmpty();
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).instrumentCode()).isEqualTo(MarketInstrumentCode.USD_KRW);
        assertThat(result.rows().get(0).observedAt()).isEqualTo(Instant.parse("2026-03-15T00:00:00Z"));
    }

    // XLSX 임포트는 첫 시트의 표 데이터를 row로 해석해야 한다.
    @Test
    void parse_reads_xlsx_rows() throws Exception {
        // Given: XLSX 파일이 준비되어 있다.
        MarketSnapshotImportService service = new MarketSnapshotImportService();
        byte[] fileBytes = createXlsx(
            new String[] {"instrument_code", "observed_at", "price_value"},
            new String[] {"XAU_USD", "2026-03-15", "3012.12"}
        );
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "market.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            fileBytes
        );

        // When: XLSX를 파싱하면
        MarketSnapshotImportParsedResult result = service.parse(file, null);

        // Then: row가 정상 해석되어야 한다.
        assertThat(result.failures()).isEmpty();
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).instrumentCode()).isEqualTo(MarketInstrumentCode.XAU_USD);
        assertThat(result.rows().get(0).priceValue()).isEqualByComparingTo("3012.12");
    }

    // 잘못된 파일 형식은 예외로 거부해야 한다.
    @Test
    void parse_rejects_unsupported_file_type() {
        // Given: 지원하지 않는 확장자의 파일이 준비되어 있다.
        MarketSnapshotImportService service = new MarketSnapshotImportService();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "market.txt",
            "text/plain",
            "dummy".getBytes()
        );

        // When & Then: 지원하지 않는 파일 형식이면 예외가 발생해야 한다.
        assertThatThrownBy(() -> service.parse(file, null))
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).getErrorCode())
            .isEqualTo(ErrorCode.MARKET_IMPORT_FORMAT_UNSUPPORTED);
    }

    private byte[] createXlsx(String[] headerValues, String[] bodyValues) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("market");
            Row headerRow = sheet.createRow(0);
            for (int index = 0; index < headerValues.length; index++) {
                headerRow.createCell(index).setCellValue(headerValues[index]);
            }
            Row bodyRow = sheet.createRow(1);
            for (int index = 0; index < bodyValues.length; index++) {
                bodyRow.createCell(index).setCellValue(bodyValues[index]);
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}

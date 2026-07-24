package com.crowdmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowdmanagement.dto.ApiDtos.AnalyticsSummaryResponse;
import com.crowdmanagement.dto.ApiDtos.HourlyAnalyticsResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class PdfReportServiceTest {

    private final PdfReportService service = new PdfReportService();

    @Test
    void buildReport_producesValidPdfBytes() {
        AnalyticsSummaryResponse summary = new AnalyticsSummaryResponse(1L, 24, 3, 30.0, 50, 15.0, 2);
        List<HourlyAnalyticsResponse> hourly = List.of(
            new HourlyAnalyticsResponse(1, 2, 20.0, 10.0),
            new HourlyAnalyticsResponse(2, 1, 50.0, 25.0)
        );

        byte[] pdf = service.buildReport(1L, 24, summary, hourly);

        assertThat(pdf).isNotEmpty();
        // A well-formed PDF must start with the "%PDF-" magic header.
        String header = new String(pdf, 0, 5, StandardCharsets.US_ASCII);
        assertThat(header).isEqualTo("%PDF-");
    }

    @Test
    void buildReport_withManyHourlyRows_paginatesWithoutError() {
        AnalyticsSummaryResponse summary = new AnalyticsSummaryResponse(1L, 720, 100, 40.0, 90, 20.0, 9);
        List<HourlyAnalyticsResponse> manyRows = java.util.stream.IntStream.range(0, 80)
            .mapToObj(i -> new HourlyAnalyticsResponse(i % 24, i, i * 1.5, i * 0.5))
            .toList();

        byte[] pdf = service.buildReport(1L, 720, summary, manyRows);

        assertThat(pdf.length).isGreaterThan(200);
    }
}

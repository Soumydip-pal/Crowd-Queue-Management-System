package com.crowdmanagement.controller;

import com.crowdmanagement.dto.ApiDtos.AnalyticsSummaryResponse;
import com.crowdmanagement.dto.ApiDtos.HourlyAnalyticsResponse;
import com.crowdmanagement.service.AnalyticsService;
import com.crowdmanagement.service.PdfReportService;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsService analyticsService;
    private final PdfReportService pdfReportService;

    public AnalyticsController(AnalyticsService analyticsService, PdfReportService pdfReportService) {
        this.analyticsService = analyticsService;
        this.pdfReportService = pdfReportService;
    }

    @GetMapping("/summary")
    public AnalyticsSummaryResponse summary(
        @RequestParam Long counterId,
        @RequestParam(defaultValue = "24") int hours
    ) {
        return analyticsService.summary(counterId, hours);
    }

    @GetMapping("/hourly")
    public List<HourlyAnalyticsResponse> hourly(
        @RequestParam Long counterId,
        @RequestParam(defaultValue = "24") int hours
    ) {
        return analyticsService.hourly(counterId, hours);
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(
        @RequestParam Long counterId,
        @RequestParam(defaultValue = "24") int hours
    ) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=queue-history.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(analyticsService.csv(counterId, hours));
    }

    @GetMapping(value = "/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportPdf(
        @RequestParam Long counterId,
        @RequestParam(defaultValue = "24") int hours
    ) {
        AnalyticsSummaryResponse summary = analyticsService.summary(counterId, hours);
        List<HourlyAnalyticsResponse> hourly = analyticsService.hourly(counterId, hours);
        byte[] pdf = pdfReportService.buildReport(counterId, hours, summary, hourly);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=queue-report.pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }
}

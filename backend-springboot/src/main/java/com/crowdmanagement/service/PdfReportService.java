package com.crowdmanagement.service;

import com.crowdmanagement.dto.ApiDtos.AnalyticsSummaryResponse;
import com.crowdmanagement.dto.ApiDtos.HourlyAnalyticsResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

/**
 * Generates a simple, single-page-per-section PDF analytics report
 * (Phase 6 of the roadmap: "PDF report generation"). Complements the
 * existing CSV export in AnalyticsController - CSV is for raw data
 * processing, this PDF is for sharing a human-readable summary.
 */
@Service
public class PdfReportService {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final float MARGIN = 50f;
    private static final float LINE_HEIGHT = 16f;

    public byte[] buildReport(
        Long counterId,
        int hours,
        AnalyticsSummaryResponse summary,
        List<HourlyAnalyticsResponse> hourly
    ) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDType1Font titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font headerFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            float y = page.getMediaBox().getHeight() - MARGIN;
            PDPageContentStream content = new PDPageContentStream(document, page);

            y = writeLine(content, titleFont, 18, MARGIN, y, "Crowd & Queue Analytics Report");
            y -= 6;
            y = writeLine(content, bodyFont, 10, MARGIN, y,
                "Generated: " + OffsetDateTime.now().format(TIMESTAMP_FORMAT));
            y = writeLine(content, bodyFont, 10, MARGIN, y,
                "Counter ID: " + counterId + "   |   Window: last " + hours + " hour(s)");
            y -= 14;

            y = writeLine(content, headerFont, 13, MARGIN, y, "Summary");
            y -= 4;
            y = writeLine(content, bodyFont, 11, MARGIN, y,
                "Snapshots recorded: " + summary.snapshotCount());
            y = writeLine(content, bodyFont, 11, MARGIN, y,
                "Average crowd length: " + summary.averageCrowdLength());
            y = writeLine(content, bodyFont, 11, MARGIN, y,
                "Peak crowd length: " + summary.peakCrowdLength());
            y = writeLine(content, bodyFont, 11, MARGIN, y,
                "Average predicted wait (min): " + summary.averagePredictedWaitMin());
            y = writeLine(content, bodyFont, 11, MARGIN, y,
                "Busiest hour: " + (summary.busiestHour() == null ? "n/a" : summary.busiestHour() + ":00"));
            y -= 18;

            y = writeLine(content, headerFont, 13, MARGIN, y, "Hourly Breakdown");
            y -= 4;

            float colHour = MARGIN;
            float colCount = MARGIN + 100;
            float colAvgCrowd = MARGIN + 220;
            float colAvgWait = MARGIN + 360;

            content.setFont(headerFont, 10);
            content.beginText();
            content.newLineAtOffset(colHour, y);
            content.showText("Hour");
            content.endText();
            content.beginText();
            content.newLineAtOffset(colCount, y);
            content.showText("Snapshots");
            content.endText();
            content.beginText();
            content.newLineAtOffset(colAvgCrowd, y);
            content.showText("Avg Crowd");
            content.endText();
            content.beginText();
            content.newLineAtOffset(colAvgWait, y);
            content.showText("Avg Wait (min)");
            content.endText();
            y -= LINE_HEIGHT;

            content.setFont(bodyFont, 10);
            for (HourlyAnalyticsResponse row : hourly) {
                if (y < MARGIN + LINE_HEIGHT) {
                    content.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    y = page.getMediaBox().getHeight() - MARGIN;
                    content.setFont(bodyFont, 10);
                }
                content.beginText();
                content.newLineAtOffset(colHour, y);
                content.showText(row.hour() + ":00");
                content.endText();
                content.beginText();
                content.newLineAtOffset(colCount, y);
                content.showText(String.valueOf(row.snapshotCount()));
                content.endText();
                content.beginText();
                content.newLineAtOffset(colAvgCrowd, y);
                content.showText(String.valueOf(row.averageCrowdLength()));
                content.endText();
                content.beginText();
                content.newLineAtOffset(colAvgWait, y);
                content.showText(String.valueOf(row.averagePredictedWaitMin()));
                content.endText();
                y -= LINE_HEIGHT;
            }

            content.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate PDF report", e);
        }
    }

    private float writeLine(
        PDPageContentStream content,
        PDType1Font font,
        float fontSize,
        float x,
        float y,
        String text
    ) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
        return y - LINE_HEIGHT;
    }
}

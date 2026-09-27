package com.crowdmanagement.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log =
        LoggerFactory.getLogger(NotificationService.class);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String resendApiKey;
    private final String fromEmail;

    public NotificationService(
        @Value("${RESEND_API_KEY:}") String resendApiKey,
        @Value("${ALERT_FROM_EMAIL:}") String fromEmail
    ) {
        this.resendApiKey = resendApiKey;
        this.fromEmail = fromEmail;
    }

    public boolean sendWaitTimeAlert(
        String recipientEmail,
        String counterName,
        int predictedWaitMinutes,
        int queueLength
    ) {
        if (resendApiKey.isBlank() || fromEmail.isBlank()) {
            log.warn("Email alerts are disabled because Resend is not configured");
            return false;
        }

        String subject = "Queue wait-time alert: " + counterName;
        String message =
            "The predicted wait time at " + counterName + " is now "
                + predictedWaitMinutes + " minutes. "
                + "Current queue length: " + queueLength + ".";

        String payload = "{"
            + "\"from\":\"" + escapeJson(fromEmail) + "\","
            + "\"to\":[\"" + escapeJson(recipientEmail) + "\"],"
            + "\"subject\":\"" + escapeJson(subject) + "\","
            + "\"text\":\"" + escapeJson(message) + "\""
            + "}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.resend.com/emails"))
            .header("Authorization", "Bearer " + resendApiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Wait-time alert email sent to {}", recipientEmail);
                return true;
            }

            log.warn(
                "Resend rejected alert email. HTTP status: {}",
                response.statusCode()
            );
        } catch (Exception exception) {
            log.error("Could not send wait-time alert email", exception);
        }

        return false;
    }

    private String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }
}

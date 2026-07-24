package com.crowdmanagement.dto;

import com.crowdmanagement.model.CounterStatus;
import com.crowdmanagement.model.NotifyChannel;
import com.crowdmanagement.model.SnapshotSource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {
    }

    public record LocationRequest(@NotBlank String name, @NotBlank String address) {
    }

    public record LocationResponse(Long id, String name, String address) {
    }

    public record CounterRequest(
        @NotNull Long locationId,
        @NotBlank String name,
        CounterStatus status,
        @Min(1) Integer serviceRatePerHour
    ) {
    }

    public record CounterUpdateRequest(CounterStatus status, @Min(1) Integer serviceRatePerHour) {
    }

    public record CounterResponse(
        Long id,
        Long locationId,
        String locationName,
        String name,
        CounterStatus status,
        Integer serviceRatePerHour
    ) {
    }

    public record QueueUpdateRequest(
        @NotNull Long counterId,
        @NotNull @Min(0) Integer currentLength,
        Integer avgWaitTimeMin,
        SnapshotSource source
    ) {
    }

    public record QueueSnapshotResponse(
        Long id,
        Long counterId,
        OffsetDateTime timestamp,
        Integer currentLength,
        Integer avgWaitTimeMin,
        SnapshotSource source
    ) {
    }

    public record PredictionResponse(
        Long counterId,
        OffsetDateTime tsFor,
        Integer predictedWaitMin,
        String modelVersion,
        List<BestSlotResponse> bestSlotToday
    ) {
    }

    public record BestSlotResponse(OffsetDateTime start, Integer predictedWaitMin) {
    }

    public record LiveCounterPayload(
        Long counterId,
        Long locationId,
        String counterName,
        OffsetDateTime timestamp,
        Integer currentLength,
        Integer avgWaitTimeMin,
        Integer predictedWaitMin,
        String status,
        String source
    ) {
    }

    public record AlertSubscriptionRequest(
        @NotNull Long counterId,
        @NotNull @Min(1) Integer thresholdWaitMin,
        NotifyChannel notifyChannel
    ) {
    }

    public record AlertSubscriptionResponse(
        Long id,
        Long counterId,
        String counterName,
        Integer thresholdWaitMin,
        NotifyChannel notifyChannel,
        boolean active
    ) {
    }

    public record AnalyticsSummaryResponse(
        Long counterId,
        Integer hours,
        long snapshotCount,
        double averageCrowdLength,
        int peakCrowdLength,
        double averagePredictedWaitMin,
        Integer busiestHour
    ) {
    }

    public record HourlyAnalyticsResponse(
        Integer hour,
        long snapshotCount,
        double averageCrowdLength,
        double averagePredictedWaitMin
    ) {
    }
}

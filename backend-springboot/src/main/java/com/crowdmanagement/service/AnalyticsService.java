package com.crowdmanagement.service;

import com.crowdmanagement.dto.ApiDtos.AnalyticsSummaryResponse;
import com.crowdmanagement.dto.ApiDtos.HourlyAnalyticsResponse;
import com.crowdmanagement.model.Prediction;
import com.crowdmanagement.model.QueueSnapshot;
import com.crowdmanagement.repository.PredictionRepository;
import com.crowdmanagement.repository.QueueSnapshotRepository;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {
    private final QueueSnapshotRepository snapshotRepository;
    private final PredictionRepository predictionRepository;

    public AnalyticsService(
        QueueSnapshotRepository snapshotRepository,
        PredictionRepository predictionRepository
    ) {
        this.snapshotRepository = snapshotRepository;
        this.predictionRepository = predictionRepository;
    }

    public AnalyticsSummaryResponse summary(Long counterId, int hours) {
        WindowData data = loadWindow(counterId, hours);
        double averageCrowd = data.snapshots().stream()
            .mapToInt(QueueSnapshot::getCurrentLength)
            .average()
            .orElse(0);
        int peakCrowd = data.snapshots().stream()
            .mapToInt(QueueSnapshot::getCurrentLength)
            .max()
            .orElse(0);
        double averageWait = data.predictions().stream()
            .mapToInt(Prediction::getPredictedWaitMin)
            .average()
            .orElse(0);
        Integer busiestHour = data.snapshots().stream()
            .collect(Collectors.groupingBy(
                snapshot -> snapshot.getTimestamp().getHour(),
                Collectors.averagingInt(QueueSnapshot::getCurrentLength)
            ))
            .entrySet()
            .stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);

        return new AnalyticsSummaryResponse(
            counterId,
            hours,
            data.snapshots().size(),
            round(averageCrowd),
            peakCrowd,
            round(averageWait),
            busiestHour
        );
    }

    public List<HourlyAnalyticsResponse> hourly(Long counterId, int hours) {
        WindowData data = loadWindow(counterId, hours);
        Map<Integer, List<QueueSnapshot>> snapshotsByHour = data.snapshots().stream()
            .collect(Collectors.groupingBy(snapshot -> snapshot.getTimestamp().getHour()));
        Map<Integer, List<Prediction>> predictionsByHour = data.predictions().stream()
            .collect(Collectors.groupingBy(prediction -> prediction.getTsFor().getHour()));

        return snapshotsByHour.entrySet()
            .stream()
            .map(entry -> {
                int hour = entry.getKey();
                double averageCrowd = entry.getValue().stream()
                    .mapToInt(QueueSnapshot::getCurrentLength)
                    .average()
                    .orElse(0);
                double averageWait = predictionsByHour.getOrDefault(hour, List.of()).stream()
                    .mapToInt(Prediction::getPredictedWaitMin)
                    .average()
                    .orElse(0);
                return new HourlyAnalyticsResponse(
                    hour,
                    entry.getValue().size(),
                    round(averageCrowd),
                    round(averageWait)
                );
            })
            .sorted(Comparator.comparing(HourlyAnalyticsResponse::hour))
            .toList();
    }

    public String csv(Long counterId, int hours) {
        WindowData data = loadWindow(counterId, hours);
        StringBuilder builder = new StringBuilder("timestamp,counter_id,current_length,avg_wait_time_min,source\n");
        for (QueueSnapshot snapshot : data.snapshots()) {
            builder.append(snapshot.getTimestamp()).append(',')
                .append(snapshot.getCounter().getId()).append(',')
                .append(snapshot.getCurrentLength()).append(',')
                .append(snapshot.getAvgWaitTimeMin() == null ? "" : snapshot.getAvgWaitTimeMin()).append(',')
                .append(snapshot.getSource()).append('\n');
        }
        return builder.toString();
    }

    private WindowData loadWindow(Long counterId, int hours) {
        int boundedHours = Math.min(Math.max(hours, 1), 24 * 30);
        OffsetDateTime to = OffsetDateTime.now();
        OffsetDateTime from = to.minusHours(boundedHours);
        return new WindowData(
            snapshotRepository.findByCounterIdAndTimestampBetweenOrderByTimestampAsc(counterId, from, to),
            predictionRepository.findByCounterIdAndTsForBetweenOrderByTsForAsc(counterId, from, to)
        );
    }

    private double round(double value) {
        return Double.parseDouble(String.format(Locale.US, "%.2f", value));
    }

    private record WindowData(List<QueueSnapshot> snapshots, List<Prediction> predictions) {
    }
}

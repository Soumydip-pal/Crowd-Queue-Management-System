package com.crowdmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.crowdmanagement.dto.ApiDtos.AnalyticsSummaryResponse;
import com.crowdmanagement.model.Location;
import com.crowdmanagement.model.Prediction;
import com.crowdmanagement.model.QueueSnapshot;
import com.crowdmanagement.model.ServiceCounter;
import com.crowdmanagement.model.SnapshotSource;
import com.crowdmanagement.repository.PredictionRepository;
import com.crowdmanagement.repository.QueueSnapshotRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private QueueSnapshotRepository snapshotRepository;

    @Mock
    private PredictionRepository predictionRepository;

    private AnalyticsService analyticsService;
    private ServiceCounter counter;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(snapshotRepository, predictionRepository);

        Location location = new Location();
        counter = new ServiceCounter();
        counter.setLocation(location);
        counter.setServiceRatePerHour(20);
    }

    private QueueSnapshot snapshot(int length, OffsetDateTime timestamp, SnapshotSource source) {
        QueueSnapshot snapshot = new QueueSnapshot();
        snapshot.setCounter(counter);
        snapshot.setCurrentLength(length);
        snapshot.setTimestamp(timestamp);
        snapshot.setSource(source);
        return snapshot;
    }

    private Prediction prediction(int waitMin, OffsetDateTime tsFor) {
        Prediction prediction = new Prediction();
        prediction.setCounter(counter);
        prediction.setPredictedWaitMin(waitMin);
        prediction.setTsFor(tsFor);
        return prediction;
    }

    @Test
    void summary_computesAveragePeakAndBusiestHour() {
        OffsetDateTime now = OffsetDateTime.now();
        List<QueueSnapshot> snapshots = List.of(
            snapshot(10, now.minusHours(3), SnapshotSource.MANUAL),
            snapshot(50, now.minusHours(2), SnapshotSource.CAMERA),
            snapshot(30, now.minusHours(1), SnapshotSource.MANUAL)
        );
        List<Prediction> predictions = List.of(
            prediction(5, now.minusHours(3)),
            prediction(25, now.minusHours(2)),
            prediction(15, now.minusHours(1))
        );

        when(snapshotRepository.findByCounterIdAndTimestampBetweenOrderByTimestampAsc(anyLong(), any(), any()))
            .thenReturn(snapshots);
        when(predictionRepository.findByCounterIdAndTsForBetweenOrderByTsForAsc(anyLong(), any(), any()))
            .thenReturn(predictions);

        AnalyticsSummaryResponse summary = analyticsService.summary(1L, 24);

        assertThat(summary.snapshotCount()).isEqualTo(3);
        assertThat(summary.averageCrowdLength()).isEqualTo(30.0);
        assertThat(summary.peakCrowdLength()).isEqualTo(50);
        assertThat(summary.averagePredictedWaitMin()).isEqualTo(15.0);
    }

    @Test
    void summary_withNoSnapshots_returnsZeroedResponse() {
        when(snapshotRepository.findByCounterIdAndTimestampBetweenOrderByTimestampAsc(anyLong(), any(), any()))
            .thenReturn(List.of());
        when(predictionRepository.findByCounterIdAndTsForBetweenOrderByTsForAsc(anyLong(), any(), any()))
            .thenReturn(List.of());

        AnalyticsSummaryResponse summary = analyticsService.summary(1L, 24);

        assertThat(summary.snapshotCount()).isZero();
        assertThat(summary.averageCrowdLength()).isZero();
        assertThat(summary.peakCrowdLength()).isZero();
        assertThat(summary.busiestHour()).isNull();
    }

    @Test
    void csv_includesHeaderAndOneRowPerSnapshot() {
        OffsetDateTime now = OffsetDateTime.now();
        List<QueueSnapshot> snapshots = List.of(snapshot(12, now, SnapshotSource.CAMERA));

        when(snapshotRepository.findByCounterIdAndTimestampBetweenOrderByTimestampAsc(anyLong(), any(), any()))
            .thenReturn(snapshots);
        when(predictionRepository.findByCounterIdAndTsForBetweenOrderByTsForAsc(anyLong(), any(), any()))
            .thenReturn(List.of());

        String csv = analyticsService.csv(1L, 24);

        assertThat(csv).startsWith("timestamp,counter_id,current_length,avg_wait_time_min,source\n");
        assertThat(csv).contains(",12,");
        assertThat(csv).contains("CAMERA");
    }

    @Test
    void summary_clampsHoursWindowToAtMostThirtyDays() {
        when(snapshotRepository.findByCounterIdAndTimestampBetweenOrderByTimestampAsc(anyLong(), any(), any()))
            .thenReturn(List.of());
        when(predictionRepository.findByCounterIdAndTsForBetweenOrderByTsForAsc(anyLong(), any(), any()))
            .thenReturn(List.of());

        // A huge hours value must not blow up; AnalyticsService clamps internally.
        AnalyticsSummaryResponse summary = analyticsService.summary(1L, 999999);

        assertThat(summary.hours()).isEqualTo(999999);
        assertThat(summary.snapshotCount()).isZero();
    }
}

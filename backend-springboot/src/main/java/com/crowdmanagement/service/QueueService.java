package com.crowdmanagement.service;

import com.crowdmanagement.dto.ApiDtos.PredictionResponse;
import com.crowdmanagement.dto.ApiDtos.QueueSnapshotResponse;
import com.crowdmanagement.dto.ApiDtos.QueueUpdateRequest;
import com.crowdmanagement.dto.ApiDtos.LiveCounterPayload;
import com.crowdmanagement.model.QueueSnapshot;
import com.crowdmanagement.model.ServiceCounter;
import com.crowdmanagement.model.SnapshotSource;
import com.crowdmanagement.repository.CounterRepository;
import com.crowdmanagement.repository.QueueSnapshotRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueueService {
    private final CounterRepository counterRepository;
    private final QueueSnapshotRepository snapshotRepository;
    private final PredictionService predictionService;
    private final LiveUpdateService liveUpdateService;

    public QueueService(
        CounterRepository counterRepository,
        QueueSnapshotRepository snapshotRepository,
        PredictionService predictionService,
        LiveUpdateService liveUpdateService
    ) {
        this.counterRepository = counterRepository;
        this.snapshotRepository = snapshotRepository;
        this.predictionService = predictionService;
        this.liveUpdateService = liveUpdateService;
    }

    @Transactional
    public LiveCounterPayload recordSnapshot(QueueUpdateRequest request) {
        ServiceCounter counter = getCounter(request.counterId());
        QueueSnapshot snapshot = new QueueSnapshot();
        snapshot.setCounter(counter);
        snapshot.setCurrentLength(request.currentLength());
        snapshot.setAvgWaitTimeMin(request.avgWaitTimeMin());
        snapshot.setSource(request.source() == null ? SnapshotSource.MANUAL : request.source());
        QueueSnapshot saved = snapshotRepository.save(snapshot);
        PredictionResponse prediction = predictionService.predictNow(counter);
        LiveCounterPayload payload = toLivePayload(saved, prediction);
        liveUpdateService.publishCounterUpdate(payload);
        return payload;
    }

    public QueueSnapshotResponse latest(Long counterId) {
        return snapshotRepository.findFirstByCounterIdOrderByTimestampDesc(counterId)
            .map(this::toResponse)
            .orElseThrow(() -> new IllegalArgumentException("No queue snapshot found"));
    }

    public LiveCounterPayload liveStatus(Long counterId) {
        ServiceCounter counter = getCounter(counterId);
        QueueSnapshot snapshot = snapshotRepository.findFirstByCounterIdOrderByTimestampDesc(counterId)
            .orElseGet(() -> {
                QueueSnapshot empty = new QueueSnapshot();
                empty.setCounter(counter);
                empty.setCurrentLength(0);
                empty.setAvgWaitTimeMin(0);
                empty.setSource(SnapshotSource.API);
                return empty;
            });
        return toLivePayload(snapshot, predictionService.previewNow(counter));
    }

    public List<QueueSnapshotResponse> history(Long counterId, OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime end = to == null ? OffsetDateTime.now() : to;
        OffsetDateTime start = from == null ? end.minusHours(24) : from;
        return snapshotRepository.findByCounterIdAndTimestampBetweenOrderByTimestampAsc(counterId, start, end)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    public PredictionResponse predictNow(Long counterId) {
        return predictionService.predictNow(getCounter(counterId));
    }

    public ServiceCounter getCounter(Long counterId) {
        return counterRepository.findById(counterId)
            .orElseThrow(() -> new IllegalArgumentException("Counter not found"));
    }

    private QueueSnapshotResponse toResponse(QueueSnapshot snapshot) {
        return new QueueSnapshotResponse(
            snapshot.getId(),
            snapshot.getCounter().getId(),
            snapshot.getTimestamp(),
            snapshot.getCurrentLength(),
            snapshot.getAvgWaitTimeMin(),
            snapshot.getSource()
        );
    }

    private LiveCounterPayload toLivePayload(QueueSnapshot snapshot, PredictionResponse prediction) {
        ServiceCounter counter = snapshot.getCounter();
        int predictedWait = prediction.predictedWaitMin();
        String status = snapshot.getCurrentLength() >= 80 || predictedWait >= 30 ? "Overcrowded" : "Normal";
        return new LiveCounterPayload(
            counter.getId(),
            counter.getLocation().getId(),
            counter.getName(),
            snapshot.getTimestamp(),
            snapshot.getCurrentLength(),
            snapshot.getAvgWaitTimeMin(),
            predictedWait,
            status,
            snapshot.getSource().name()
        );
    }
}

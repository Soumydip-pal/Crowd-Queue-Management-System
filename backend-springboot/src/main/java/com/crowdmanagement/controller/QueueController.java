package com.crowdmanagement.controller;

import com.crowdmanagement.dto.ApiDtos.PredictionResponse;
import com.crowdmanagement.dto.ApiDtos.LiveCounterPayload;
import com.crowdmanagement.dto.ApiDtos.QueueSnapshotResponse;
import com.crowdmanagement.dto.ApiDtos.QueueUpdateRequest;
import com.crowdmanagement.service.QueueService;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class QueueController {
    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping("/queue")
    public LiveCounterPayload postQueue(@Valid @RequestBody QueueUpdateRequest request) {
        return queueService.recordSnapshot(request);
    }

    @GetMapping("/queue/latest")
    public QueueSnapshotResponse latest(@RequestParam Long counterId) {
        return queueService.latest(counterId);
    }

    @GetMapping("/queue/live")
    public LiveCounterPayload liveStatus(@RequestParam Long counterId) {
        return queueService.liveStatus(counterId);
    }

    @GetMapping("/queue/history")
    public List<QueueSnapshotResponse> history(
        @RequestParam Long counterId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        return queueService.history(counterId, from, to);
    }

    @GetMapping("/predict/now")
    public PredictionResponse predictNow(@RequestParam Long counterId) {
        return queueService.predictNow(counterId);
    }

    @GetMapping("/crowd-status")
    public LiveCounterPayload legacyCrowdStatus(@RequestParam(defaultValue = "1") Long counterId) {
        return queueService.liveStatus(counterId);
    }
}

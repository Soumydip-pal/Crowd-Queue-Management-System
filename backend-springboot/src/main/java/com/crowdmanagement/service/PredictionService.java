package com.crowdmanagement.service;

import com.crowdmanagement.dto.ApiDtos.BestSlotResponse;
import com.crowdmanagement.dto.ApiDtos.PredictionResponse;
import com.crowdmanagement.model.Prediction;
import com.crowdmanagement.model.QueueSnapshot;
import com.crowdmanagement.model.ServiceCounter;
import com.crowdmanagement.repository.PredictionRepository;
import com.crowdmanagement.repository.QueueSnapshotRepository;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class PredictionService {
    private final QueueSnapshotRepository snapshotRepository;
    private final PredictionRepository predictionRepository;
    private final RestTemplate restTemplate;
    private final String mlServiceUrl;

    public PredictionService(
        QueueSnapshotRepository snapshotRepository,
        PredictionRepository predictionRepository,
        RestTemplate restTemplate,
        @Value("${app.ml-service.url}") String mlServiceUrl
    ) {
        this.snapshotRepository = snapshotRepository;
        this.predictionRepository = predictionRepository;
        this.restTemplate = restTemplate;
        this.mlServiceUrl = mlServiceUrl;
    }

    public PredictionResponse predictNow(ServiceCounter counter) {
        return buildPrediction(counter, true);
    }

    public PredictionResponse previewNow(ServiceCounter counter) {
        return buildPrediction(counter, false);
    }

    private PredictionResponse buildPrediction(ServiceCounter counter, boolean persist) {
        QueueSnapshot latest = snapshotRepository.findFirstByCounterIdOrderByTimestampDesc(counter.getId())
            .orElse(null);
        int currentLength = latest == null ? 0 : latest.getCurrentLength();
        MlPrediction mlPrediction = requestMlPrediction(counter, currentLength);
        int predicted = mlPrediction.predictedWaitMin();
        OffsetDateTime tsFor = OffsetDateTime.now();
        String modelVersion = mlPrediction.modelVersion();

        if (persist) {
            Prediction prediction = new Prediction();
            prediction.setCounter(counter);
            prediction.setTsFor(tsFor);
            prediction.setPredictedWaitMin(predicted);
            prediction.setModelVersion(modelVersion);
            prediction.setFeaturesJson(mlPrediction.featuresJson());
            predictionRepository.save(prediction);
        }

        return new PredictionResponse(
            counter.getId(),
            tsFor,
            predicted,
            modelVersion,
            mlPrediction.bestSlots().isEmpty() ? bestSlots(counter, currentLength) : mlPrediction.bestSlots()
        );
    }

    public int baselineWait(int currentLength, int serviceRatePerHour) {
        if (currentLength <= 0) {
            return 0;
        }
        double minutesPerPerson = 60.0 / Math.max(serviceRatePerHour, 1);
        return (int) Math.ceil(currentLength * minutesPerPerson);
    }

    private List<BestSlotResponse> bestSlots(ServiceCounter counter, int currentLength) {
        OffsetDateTime now = OffsetDateTime.now();
        return IntStream.rangeClosed(1, 24)
            .mapToObj(slot -> {
                OffsetDateTime start = now.plusMinutes(slot * 15L);
                int projectedLength = Math.max(0, currentLength - (slot / 2));
                return new BestSlotResponse(start, baselineWait(projectedLength, counter.getServiceRatePerHour()));
            })
            .sorted(Comparator.comparing(BestSlotResponse::predictedWaitMin))
            .limit(3)
            .toList();
    }

    private MlPrediction requestMlPrediction(ServiceCounter counter, int currentLength) {
        int arrivalRate = recentArrivalRatePerHour(counter.getId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("counter_id", counter.getId());
        payload.put("current_length", currentLength);
        payload.put("service_rate_per_hour", counter.getServiceRatePerHour());
        payload.put("arrival_rate_per_hour", arrivalRate);
        payload.put("time_of_day", OffsetDateTime.now().getHour());
        payload.put("day_of_week", OffsetDateTime.now().getDayOfWeek().getValue());

        try {
            Map response = restTemplate.postForObject(mlServiceUrl + "/ml/predict", payload, Map.class);
            if (response == null) {
                return fallbackPrediction(currentLength, counter.getServiceRatePerHour(), arrivalRate);
            }
            int predictedWait = numberAsInt(response.get("predicted_wait_min"), baselineWait(currentLength, counter.getServiceRatePerHour()));
            String modelVersion = String.valueOf(response.getOrDefault("model_version", "ml-service-v1"));
            return new MlPrediction(
                predictedWait,
                modelVersion,
                toBestSlots(response.get("best_slot_today")),
                "{\"currentLength\":" + currentLength
                    + ",\"serviceRatePerHour\":" + counter.getServiceRatePerHour()
                    + ",\"arrivalRatePerHour\":" + arrivalRate
                    + ",\"source\":\"ml-service\"}"
            );
        } catch (RestClientException ex) {
            return fallbackPrediction(currentLength, counter.getServiceRatePerHour(), arrivalRate);
        }
    }

    private MlPrediction fallbackPrediction(int currentLength, int serviceRatePerHour, int arrivalRate) {
        return new MlPrediction(
            baselineWait(currentLength, serviceRatePerHour),
            "baseline-v1",
            List.of(),
            "{\"currentLength\":" + currentLength
                + ",\"serviceRatePerHour\":" + serviceRatePerHour
                + ",\"arrivalRatePerHour\":" + arrivalRate
                + ",\"source\":\"spring-fallback\"}"
        );
    }

    private int recentArrivalRatePerHour(Long counterId) {
        List<QueueSnapshot> snapshots = snapshotRepository.findTop2ByCounterIdOrderByTimestampDesc(counterId);
        if (snapshots.size() < 2) {
            return 0;
        }
        QueueSnapshot latest = snapshots.get(0);
        QueueSnapshot previous = snapshots.get(1);
        long minutes = Math.max(1, java.time.Duration.between(previous.getTimestamp(), latest.getTimestamp()).toMinutes());
        int delta = Math.max(0, latest.getCurrentLength() - previous.getCurrentLength());
        return (int) Math.round(delta * (60.0 / minutes));
    }

    private int numberAsInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return (int) Math.round(number.doubleValue());
        }
        return defaultValue;
    }

    private List<BestSlotResponse> toBestSlots(Object rawSlots) {
        if (!(rawSlots instanceof List<?> slots)) {
            return List.of();
        }
        List<BestSlotResponse> responses = new ArrayList<>();
        for (Object item : slots) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object rawStart = map.get("start");
            if (rawStart == null) {
                continue;
            }
            try {
                responses.add(new BestSlotResponse(
                    OffsetDateTime.parse(String.valueOf(rawStart)),
                    numberAsInt(map.get("predicted_wait_min"), 0)
                ));
            } catch (DateTimeParseException ignored) {
                // Skip malformed slots from external ML service.
            }
        }
        return responses;
    }

    private record MlPrediction(
        int predictedWaitMin,
        String modelVersion,
        List<BestSlotResponse> bestSlots,
        String featuresJson
    ) {
    }
}

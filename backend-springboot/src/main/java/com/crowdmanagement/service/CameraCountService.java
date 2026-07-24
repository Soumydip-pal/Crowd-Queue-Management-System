package com.crowdmanagement.service;

import com.crowdmanagement.dto.ApiDtos.LiveCounterPayload;
import com.crowdmanagement.dto.ApiDtos.QueueUpdateRequest;
import com.crowdmanagement.model.SnapshotSource;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Phase 5 of the roadmap: camera/computer-vision crowd counting.
 *
 * Receives an uploaded camera frame from the frontend/admin console, forwards
 * it to the ML service's /ml/detect-crowd endpoint (OpenCV HOG person
 * detector, see ml-service/app.py), and records the resulting count as a
 * QueueSnapshot with source=CAMERA - reusing the existing QueueService /
 * WebSocket live-update pipeline so camera counts show up on the live
 * dashboard exactly like manual counts do.
 *
 * Raw images are never persisted; only the resulting numeric count is
 * stored, per the roadmap's "Do not store raw images by default" guidance.
 */
@Service
public class CameraCountService {
    private final RestTemplate restTemplate;
    private final QueueService queueService;
    private final String mlServiceUrl;

    public CameraCountService(
        RestTemplate restTemplate,
        QueueService queueService,
        @Value("${app.ml-service.url}") String mlServiceUrl
    ) {
        this.restTemplate = restTemplate;
        this.queueService = queueService;
        this.mlServiceUrl = mlServiceUrl;
    }

    public LiveCounterPayload countAndRecord(Long counterId, MultipartFile image, String roiJson) {
        int count = detectCount(image, roiJson);
        QueueUpdateRequest request = new QueueUpdateRequest(counterId, count, null, SnapshotSource.CAMERA);
        return queueService.recordSnapshot(request);
    }

    @SuppressWarnings("unchecked")
    private int detectCount(MultipartFile image, String roiJson) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource imageResource = new ByteArrayResource(image.getBytes()) {
                @Override
                public String getFilename() {
                    return image.getOriginalFilename() == null ? "frame.jpg" : image.getOriginalFilename();
                }
            };
            body.add("image", imageResource);
            if (roiJson != null && !roiJson.isBlank()) {
                body.add("roi", roiJson);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            Map<String, Object> response = restTemplate.postForObject(
                mlServiceUrl + "/ml/detect-crowd", entity, Map.class
            );
            if (response == null || !(response.get("count") instanceof Number countNumber)) {
                throw new IllegalStateException("ML service returned no detection count");
            }
            return countNumber.intValue();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read uploaded image", e);
        } catch (RestClientException e) {
            throw new IllegalStateException("Crowd-detection service is unavailable: " + e.getMessage(), e);
        }
    }
}

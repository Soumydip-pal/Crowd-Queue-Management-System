package com.crowdmanagement.controller;

import com.crowdmanagement.dto.ApiDtos.LiveCounterPayload;
import com.crowdmanagement.service.CameraCountService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * POST /api/camera/count - upload a camera frame for a counter; the frame is
 * sent to the ML service's OpenCV person detector, the resulting count is
 * stored as a CAMERA-sourced QueueSnapshot, and the live dashboard updates
 * over the existing WebSocket pipeline. See CameraCountService for details.
 */
@RestController
@RequestMapping("/api/camera")
public class CameraController {
    private final CameraCountService cameraCountService;

    public CameraController(CameraCountService cameraCountService) {
        this.cameraCountService = cameraCountService;
    }

    @PostMapping(value = "/count", consumes = "multipart/form-data")
    public LiveCounterPayload count(
        @RequestParam Long counterId,
        @RequestPart("image") MultipartFile image,
        @RequestParam(required = false) String roi
    ) {
        return cameraCountService.countAndRecord(counterId, image, roi);
    }
}

package com.crowdmanagement.service;

import com.crowdmanagement.dto.ApiDtos.LiveCounterPayload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class LiveUpdateService {
    private final SimpMessagingTemplate messagingTemplate;

    public LiveUpdateService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishCounterUpdate(LiveCounterPayload payload) {
        messagingTemplate.convertAndSend("/topic/counter." + payload.counterId() + ".live", payload);
        messagingTemplate.convertAndSend("/topic/location." + payload.locationId() + ".live", payload);
    }
}

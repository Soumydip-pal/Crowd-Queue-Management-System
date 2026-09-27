package com.crowdmanagement.service;

import com.crowdmanagement.dto.ApiDtos.AlertSubscriptionRequest;
import com.crowdmanagement.dto.ApiDtos.AlertSubscriptionResponse;
import com.crowdmanagement.model.AlertSubscription;
import com.crowdmanagement.model.AppUser;
import com.crowdmanagement.model.NotifyChannel;
import com.crowdmanagement.model.ServiceCounter;
import com.crowdmanagement.repository.AlertSubscriptionRepository;
import com.crowdmanagement.repository.CounterRepository;
import com.crowdmanagement.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertService {

    private static final int ALERT_COOLDOWN_MINUTES = 15;

    private final AlertSubscriptionRepository alertSubscriptionRepository;
    private final UserRepository userRepository;
    private final CounterRepository counterRepository;
    private final NotificationService notificationService;

    public AlertService(
        AlertSubscriptionRepository alertSubscriptionRepository,
        UserRepository userRepository,
        CounterRepository counterRepository,
        NotificationService notificationService
    ) {
        this.alertSubscriptionRepository = alertSubscriptionRepository;
        this.userRepository = userRepository;
        this.counterRepository = counterRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public AlertSubscriptionResponse create(
        String email,
        AlertSubscriptionRequest request
    ) {
        AppUser user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        ServiceCounter counter = counterRepository.findById(request.counterId())
            .orElseThrow(() -> new IllegalArgumentException("Counter not found"));

        AlertSubscription subscription = new AlertSubscription();
        subscription.setUser(user);
        subscription.setCounter(counter);
        subscription.setThresholdWaitMin(request.thresholdWaitMin());
        subscription.setNotifyChannel(
            request.notifyChannel() == null
                ? NotifyChannel.EMAIL
                : request.notifyChannel()
        );
        subscription.setActive(true);

        return toResponse(alertSubscriptionRepository.save(subscription));
    }

    @Transactional(readOnly = true)
    public List<AlertSubscriptionResponse> mine(String email) {
        AppUser user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return alertSubscriptionRepository.findByUserIdAndActiveTrue(user.getId())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public void notifySubscribers(
        Long counterId,
        String counterName,
        int predictedWaitMin,
        int currentQueueLength
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        for (AlertSubscription subscription :
            alertSubscriptionRepository.findActiveByCounterIdWithUser(counterId)) {

            if (subscription.getNotifyChannel() != NotifyChannel.EMAIL) {
                continue;
            }

            if (predictedWaitMin < subscription.getThresholdWaitMin()) {
                continue;
            }

            OffsetDateTime lastNotifiedAt = subscription.getLastNotifiedAt();

            if (
                lastNotifiedAt != null
                    && lastNotifiedAt.isAfter(
                        now.minusMinutes(ALERT_COOLDOWN_MINUTES)
                    )
            ) {
                continue;
            }

            boolean sent = notificationService.sendWaitTimeAlert(
                subscription.getUser().getEmail(),
                counterName,
                predictedWaitMin,
                currentQueueLength
            );

            if (sent) {
                subscription.setLastNotifiedAt(now);
            }
        }
    }

    private AlertSubscriptionResponse toResponse(
        AlertSubscription subscription
    ) {
        return new AlertSubscriptionResponse(
            subscription.getId(),
            subscription.getCounter().getId(),
            subscription.getCounter().getName(),
            subscription.getThresholdWaitMin(),
            subscription.getNotifyChannel(),
            subscription.isActive()
        );
    }
}

package com.crowdmanagement.repository;

import com.crowdmanagement.model.AlertSubscription;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertSubscriptionRepository extends JpaRepository<AlertSubscription, Long> {
    List<AlertSubscription> findByUserIdAndActiveTrue(Long userId);
}

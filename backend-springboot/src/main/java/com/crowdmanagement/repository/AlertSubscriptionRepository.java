package com.crowdmanagement.repository;

import com.crowdmanagement.model.AlertSubscription;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertSubscriptionRepository
    extends JpaRepository<AlertSubscription, Long> {

    List<AlertSubscription> findByUserIdAndActiveTrue(Long userId);

    @Query("""
        select subscription
        from AlertSubscription subscription
        join fetch subscription.user
        join fetch subscription.counter
        where subscription.counter.id = :counterId
          and subscription.active = true
        """)
    List<AlertSubscription> findActiveByCounterIdWithUser(
        @Param("counterId") Long counterId
    );
}

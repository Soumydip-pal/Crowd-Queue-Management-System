package com.crowdmanagement.repository;

import com.crowdmanagement.model.Prediction;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {
    List<Prediction> findByCounterIdAndTsForBetweenOrderByTsForAsc(
        Long counterId,
        OffsetDateTime from,
        OffsetDateTime to
    );
}

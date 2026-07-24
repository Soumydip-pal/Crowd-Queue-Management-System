package com.crowdmanagement.repository;

import com.crowdmanagement.model.QueueSnapshot;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueueSnapshotRepository extends JpaRepository<QueueSnapshot, Long> {
    Optional<QueueSnapshot> findFirstByCounterIdOrderByTimestampDesc(Long counterId);

    List<QueueSnapshot> findByCounterIdAndTimestampBetweenOrderByTimestampAsc(
        Long counterId,
        OffsetDateTime from,
        OffsetDateTime to
    );

    List<QueueSnapshot> findTop2ByCounterIdOrderByTimestampDesc(Long counterId);
}

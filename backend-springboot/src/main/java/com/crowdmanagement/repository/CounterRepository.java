package com.crowdmanagement.repository;

import com.crowdmanagement.model.ServiceCounter;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CounterRepository extends JpaRepository<ServiceCounter, Long> {
    List<ServiceCounter> findByLocationId(Long locationId);

    @Query("select c from ServiceCounter c join fetch c.location")
    List<ServiceCounter> findAllWithLocation();

    @Query("select c from ServiceCounter c join fetch c.location where c.location.id = :locationId")
    List<ServiceCounter> findByLocationIdWithLocation(@Param("locationId") Long locationId);
}

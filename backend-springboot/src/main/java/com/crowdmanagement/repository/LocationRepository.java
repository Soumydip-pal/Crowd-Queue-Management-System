package com.crowdmanagement.repository;

import com.crowdmanagement.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, Long> {
}

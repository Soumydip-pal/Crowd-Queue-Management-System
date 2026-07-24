package com.crowdmanagement.service;

import com.crowdmanagement.dto.ApiDtos.CounterRequest;
import com.crowdmanagement.dto.ApiDtos.CounterResponse;
import com.crowdmanagement.dto.ApiDtos.CounterUpdateRequest;
import com.crowdmanagement.dto.ApiDtos.LocationRequest;
import com.crowdmanagement.dto.ApiDtos.LocationResponse;
import com.crowdmanagement.model.CounterStatus;
import com.crowdmanagement.model.Location;
import com.crowdmanagement.model.ServiceCounter;
import com.crowdmanagement.repository.CounterRepository;
import com.crowdmanagement.repository.LocationRepository;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationService {
    private final LocationRepository locationRepository;
    private final CounterRepository counterRepository;

    public LocationService(LocationRepository locationRepository, CounterRepository counterRepository) {
        this.locationRepository = locationRepository;
        this.counterRepository = counterRepository;
    }

    // Locations rarely change, so the full list is cached (TTL configured via
    // app.cache.locations-ttl-seconds). Cleared automatically by
    // createLocation, or manually when Redis TTL expires.
    @Cacheable("locations")
    @Transactional(readOnly = true)
    public List<LocationResponse> listLocations() {
        return locationRepository.findAll().stream().map(this::toLocationResponse).toList();
    }

    @CacheEvict(value = "locations", allEntries = true)
    @Transactional
    public LocationResponse createLocation(LocationRequest request) {
        Location location = new Location();
        location.setName(request.name().trim());
        location.setAddress(request.address().trim());
        return toLocationResponse(locationRepository.save(location));
    }

    // Keyed by locationId (including the "all counters" null case) so each
    // location's counter list caches independently; TTL kept short since
    // counter status/service-rate change more often than location metadata.
    @Cacheable(value = "counters", key = "#locationId")
    @Transactional(readOnly = true)
    public List<CounterResponse> listCounters(Long locationId) {
        List<ServiceCounter> counters = locationId == null
            ? counterRepository.findAllWithLocation()
            : counterRepository.findByLocationIdWithLocation(locationId);
        return counters.stream().map(this::toCounterResponse).toList();
    }

    @CacheEvict(value = "counters", allEntries = true)
    @Transactional
    public CounterResponse createCounter(CounterRequest request) {
        Location location = locationRepository.findById(request.locationId())
            .orElseThrow(() -> new IllegalArgumentException("Location not found"));
        ServiceCounter counter = new ServiceCounter();
        counter.setLocation(location);
        counter.setName(request.name().trim());
        counter.setStatus(request.status() == null ? CounterStatus.OPEN : request.status());
        counter.setServiceRatePerHour(request.serviceRatePerHour() == null ? 30 : request.serviceRatePerHour());
        return toCounterResponse(counterRepository.save(counter));
    }

    @CacheEvict(value = "counters", allEntries = true)
    @Transactional
    public CounterResponse updateCounter(Long id, CounterUpdateRequest request) {
        ServiceCounter counter = counterRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Counter not found"));
        if (request.status() != null) {
            counter.setStatus(request.status());
        }
        if (request.serviceRatePerHour() != null) {
            counter.setServiceRatePerHour(request.serviceRatePerHour());
        }
        return toCounterResponse(counter);
    }

    private LocationResponse toLocationResponse(Location location) {
        return new LocationResponse(location.getId(), location.getName(), location.getAddress());
    }

    private CounterResponse toCounterResponse(ServiceCounter counter) {
        return new CounterResponse(
            counter.getId(),
            counter.getLocation().getId(),
            counter.getLocation().getName(),
            counter.getName(),
            counter.getStatus(),
            counter.getServiceRatePerHour()
        );
    }
}

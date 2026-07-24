package com.crowdmanagement.controller;

import com.crowdmanagement.dto.ApiDtos.CounterRequest;
import com.crowdmanagement.dto.ApiDtos.CounterResponse;
import com.crowdmanagement.dto.ApiDtos.CounterUpdateRequest;
import com.crowdmanagement.dto.ApiDtos.LocationRequest;
import com.crowdmanagement.dto.ApiDtos.LocationResponse;
import com.crowdmanagement.service.LocationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LocationController {
    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping("/locations")
    public List<LocationResponse> listLocations() {
        return locationService.listLocations();
    }

    @PostMapping("/locations")
    public LocationResponse createLocation(@Valid @RequestBody LocationRequest request) {
        return locationService.createLocation(request);
    }

    @GetMapping("/counters")
    public List<CounterResponse> listCounters(@RequestParam(required = false) Long locationId) {
        return locationService.listCounters(locationId);
    }

    @GetMapping("/locations/counters")
    public List<CounterResponse> listLocationCounters(@RequestParam(required = false) Long locationId) {
        return locationService.listCounters(locationId);
    }

    @PostMapping("/counters")
    public CounterResponse createCounter(@Valid @RequestBody CounterRequest request) {
        return locationService.createCounter(request);
    }

    @PatchMapping("/counters/{id}")
    public CounterResponse updateCounter(
        @PathVariable Long id,
        @Valid @RequestBody CounterUpdateRequest request
    ) {
        return locationService.updateCounter(id, request);
    }
}

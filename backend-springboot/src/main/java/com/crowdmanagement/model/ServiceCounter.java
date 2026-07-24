package com.crowdmanagement.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "service_counters")
public class ServiceCounter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CounterStatus status = CounterStatus.OPEN;

    @Column(nullable = false)
    private Integer serviceRatePerHour = 30;

    public Long getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CounterStatus getStatus() {
        return status;
    }

    public void setStatus(CounterStatus status) {
        this.status = status;
    }

    public Integer getServiceRatePerHour() {
        return serviceRatePerHour;
    }

    public void setServiceRatePerHour(Integer serviceRatePerHour) {
        this.serviceRatePerHour = serviceRatePerHour;
    }
}

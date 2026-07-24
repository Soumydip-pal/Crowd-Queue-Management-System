package com.crowdmanagement.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "queue_snapshots",
    indexes = @Index(name = "idx_queue_snapshots_counter_timestamp", columnList = "counter_id,timestamp")
)
public class QueueSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "counter_id")
    private ServiceCounter counter;

    @Column(nullable = false)
    private OffsetDateTime timestamp = OffsetDateTime.now();

    @Column(nullable = false)
    private Integer currentLength;

    private Integer avgWaitTimeMin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SnapshotSource source = SnapshotSource.MANUAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    public Long getId() {
        return id;
    }

    public ServiceCounter getCounter() {
        return counter;
    }

    public void setCounter(ServiceCounter counter) {
        this.counter = counter;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getCurrentLength() {
        return currentLength;
    }

    public void setCurrentLength(Integer currentLength) {
        this.currentLength = currentLength;
    }

    public Integer getAvgWaitTimeMin() {
        return avgWaitTimeMin;
    }

    public void setAvgWaitTimeMin(Integer avgWaitTimeMin) {
        this.avgWaitTimeMin = avgWaitTimeMin;
    }

    public SnapshotSource getSource() {
        return source;
    }

    public void setSource(SnapshotSource source) {
        this.source = source;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(AppUser createdBy) {
        this.createdBy = createdBy;
    }
}

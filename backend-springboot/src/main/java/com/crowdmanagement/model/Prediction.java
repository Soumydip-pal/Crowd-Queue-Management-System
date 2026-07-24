package com.crowdmanagement.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "predictions",
    indexes = @Index(name = "idx_predictions_counter_ts_for", columnList = "counter_id,ts_for")
)
public class Prediction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "counter_id")
    private ServiceCounter counter;

    @Column(nullable = false)
    private OffsetDateTime tsFor;

    @Column(nullable = false)
    private Integer predictedWaitMin;

    @Column(nullable = false, length = 40)
    private String modelVersion = "baseline-v1";

    @Column(length = 2000)
    private String featuresJson;

    public Long getId() {
        return id;
    }

    public ServiceCounter getCounter() {
        return counter;
    }

    public void setCounter(ServiceCounter counter) {
        this.counter = counter;
    }

    public OffsetDateTime getTsFor() {
        return tsFor;
    }

    public void setTsFor(OffsetDateTime tsFor) {
        this.tsFor = tsFor;
    }

    public Integer getPredictedWaitMin() {
        return predictedWaitMin;
    }

    public void setPredictedWaitMin(Integer predictedWaitMin) {
        this.predictedWaitMin = predictedWaitMin;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getFeaturesJson() {
        return featuresJson;
    }

    public void setFeaturesJson(String featuresJson) {
        this.featuresJson = featuresJson;
    }
}

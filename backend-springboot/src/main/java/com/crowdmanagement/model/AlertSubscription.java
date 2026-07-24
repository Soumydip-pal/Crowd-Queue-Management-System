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
@Table(name = "alert_subscriptions")
public class AlertSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "counter_id")
    private ServiceCounter counter;

    @Column(nullable = false)
    private Integer thresholdWaitMin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotifyChannel notifyChannel = NotifyChannel.EMAIL;

    @Column(nullable = false)
    private boolean active = true;

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public ServiceCounter getCounter() {
        return counter;
    }

    public void setCounter(ServiceCounter counter) {
        this.counter = counter;
    }

    public Integer getThresholdWaitMin() {
        return thresholdWaitMin;
    }

    public void setThresholdWaitMin(Integer thresholdWaitMin) {
        this.thresholdWaitMin = thresholdWaitMin;
    }

    public NotifyChannel getNotifyChannel() {
        return notifyChannel;
    }

    public void setNotifyChannel(NotifyChannel notifyChannel) {
        this.notifyChannel = notifyChannel;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}

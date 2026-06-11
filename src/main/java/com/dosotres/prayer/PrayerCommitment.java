package com.dosotres.prayer;

import com.dosotres.timer.PrayerSession;
import com.dosotres.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "prayer_commitments")
public class PrayerCommitment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prayer_request_id", nullable = false)
    private PrayerRequest prayerRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "committed_date", nullable = false)
    private LocalDate committedDate;

    @Column(nullable = false)
    private boolean fulfilled = false;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private PrayerSession session;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PrayerRequest getPrayerRequest() { return prayerRequest; }
    public void setPrayerRequest(PrayerRequest prayerRequest) { this.prayerRequest = prayerRequest; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalDate getCommittedDate() { return committedDate; }
    public void setCommittedDate(LocalDate committedDate) { this.committedDate = committedDate; }

    public boolean isFulfilled() { return fulfilled; }
    public void setFulfilled(boolean fulfilled) { this.fulfilled = fulfilled; }

    public Instant getFulfilledAt() { return fulfilledAt; }
    public void setFulfilledAt(Instant fulfilledAt) { this.fulfilledAt = fulfilledAt; }

    public boolean isPrivate() { return isPrivate; }
    public void setPrivate(boolean isPrivate) { this.isPrivate = isPrivate; }

    public PrayerSession getSession() { return session; }
    public void setSession(PrayerSession session) { this.session = session; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

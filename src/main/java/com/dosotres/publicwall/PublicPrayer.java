package com.dosotres.publicwall;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "public_prayers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"public_request_id", "user_id"}))
public class PublicPrayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "public_request_id", nullable = false)
    private PublicPrayerRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Si la oración se muestra con el nombre del orante (true) o como "Anónimo" (false). */
    @Column(nullable = false)
    private boolean visible = false;

    @Column(name = "prayed_at", nullable = false, updatable = false)
    private Instant prayedAt;

    @PrePersist
    void onCreate() {
        this.prayedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PublicPrayerRequest getRequest() { return request; }
    public void setRequest(PublicPrayerRequest request) { this.request = request; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public Instant getPrayedAt() { return prayedAt; }
    public void setPrayedAt(Instant prayedAt) { this.prayedAt = prayedAt; }
}

package com.dosotres.chain;

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
import java.time.Instant;

@Entity
@Table(name = "chain_commitments")
public class ChainCommitment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chain_id", nullable = false)
    private PrayerChain chain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "slot_index", nullable = false)
    private int slotIndex;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PrayerChain getChain() { return chain; }
    public void setChain(PrayerChain chain) { this.chain = chain; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public int getSlotIndex() { return slotIndex; }
    public void setSlotIndex(int slotIndex) { this.slotIndex = slotIndex; }

    public Instant getCreatedAt() { return createdAt; }
}

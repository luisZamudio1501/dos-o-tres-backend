package com.dosotres.messaging;

import com.dosotres.publicwall.PublicPrayerRequest;
import com.dosotres.user.User;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationState state = ConversationState.ACCEPTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by", nullable = false)
    private User initiatedBy;

    /** Pedido del muro que originó la solicitud de vínculo (null = conversación común). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_public_request_id")
    private PublicPrayerRequest originPublicRequest;

    /** Snapshot del título del pedido de origen, para mostrar sin join al enmascarar. */
    @Column(name = "origin_context", length = 150)
    private String originContext;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ConversationState getState() { return state; }
    public void setState(ConversationState state) { this.state = state; }

    public User getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(User initiatedBy) { this.initiatedBy = initiatedBy; }

    public PublicPrayerRequest getOriginPublicRequest() { return originPublicRequest; }
    public void setOriginPublicRequest(PublicPrayerRequest originPublicRequest) { this.originPublicRequest = originPublicRequest; }

    public String getOriginContext() { return originContext; }
    public void setOriginContext(String originContext) { this.originContext = originContext; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(Instant lastMessageAt) { this.lastMessageAt = lastMessageAt; }
}

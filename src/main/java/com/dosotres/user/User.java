package com.dosotres.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String timezone = "America/Argentina/Buenos_Aires";

    @Column(nullable = false, length = 10)
    private String locale = "es";

    // Perfil de congregación (S5) — todos opcionales, nunca en el registro.
    // country = código ISO-3166-1 alfa-2.
    // columnDefinition explícito: V7 lo crea como CHAR(2) y ddl-auto=validate
    // exige coincidencia exacta (mismo caso que PrayerSession.id CHAR(36)).
    @Column(length = 2, columnDefinition = "CHAR(2)")
    private String country;

    @Column(length = 100)
    private String province;

    @Column(length = 100)
    private String city;

    @Column(name = "church_name", length = 150)
    private String churchName;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_tier", nullable = false)
    private SubscriptionTier subscriptionTier = SubscriptionTier.FREE;

    @Column(name = "max_groups", nullable = false)
    private Integer maxGroups = 3;

    @Column(name = "last_reengaged_on")
    private LocalDate lastReengagedOn;

    // Preferencias de notificación push, una por tipo de evento.
    @Column(name = "notify_on_request_created", nullable = false)
    private boolean notifyOnRequestCreated = true;

    @Column(name = "notify_on_prayed", nullable = false)
    private boolean notifyOnPrayed = true;

    @Column(name = "notify_on_answered", nullable = false)
    private boolean notifyOnAnswered = true;

    // Rol global de comunidad (ADR-006). Default USER; MODERATOR gestiona reportes.
    @Enumerated(EnumType.STRING)
    @Column(name = "global_role", nullable = false, length = 20)
    private GlobalRole globalRole = GlobalRole.USER;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getChurchName() {
        return churchName;
    }

    public void setChurchName(String churchName) {
        this.churchName = churchName;
    }

    public SubscriptionTier getSubscriptionTier() {
        return subscriptionTier;
    }

    public void setSubscriptionTier(SubscriptionTier subscriptionTier) {
        this.subscriptionTier = subscriptionTier;
    }

    public Integer getMaxGroups() {
        return maxGroups;
    }

    public void setMaxGroups(Integer maxGroups) {
        this.maxGroups = maxGroups;
    }

    public LocalDate getLastReengagedOn() {
        return lastReengagedOn;
    }

    public void setLastReengagedOn(LocalDate lastReengagedOn) {
        this.lastReengagedOn = lastReengagedOn;
    }

    public boolean isNotifyOnRequestCreated() {
        return notifyOnRequestCreated;
    }

    public void setNotifyOnRequestCreated(boolean notifyOnRequestCreated) {
        this.notifyOnRequestCreated = notifyOnRequestCreated;
    }

    public boolean isNotifyOnPrayed() {
        return notifyOnPrayed;
    }

    public void setNotifyOnPrayed(boolean notifyOnPrayed) {
        this.notifyOnPrayed = notifyOnPrayed;
    }

    public boolean isNotifyOnAnswered() {
        return notifyOnAnswered;
    }

    public void setNotifyOnAnswered(boolean notifyOnAnswered) {
        this.notifyOnAnswered = notifyOnAnswered;
    }

    public GlobalRole getGlobalRole() {
        return globalRole;
    }

    public void setGlobalRole(GlobalRole globalRole) {
        this.globalRole = globalRole;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public enum SubscriptionTier {
        FREE, PREMIUM
    }
}

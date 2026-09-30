package com.github.deployedreject.Aggregator_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "moderation_alerts")
public class ModerationAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plugin_id", nullable = false, foreignKey = @ForeignKey(name = "fk_alerts_plugin"))
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Plugin plugin;

    @Column(nullable = false)
    private String reason;

    @Column(name = "dislikes_at_trigger", nullable = false)
    private int dislikesAtTrigger;

    @Column(name = "alerted_at", nullable = false, updatable = false)
    private Instant alertedAt;

    @Column(nullable = false)
    private boolean resolved = false;

    public ModerationAlert() {
    }

    public ModerationAlert(Long id, Plugin plugin, String reason, int dislikesAtTrigger, boolean resolved) {
        this.id = id;
        this.plugin = plugin;
        this.reason = reason;
        this.dislikesAtTrigger = dislikesAtTrigger;
        this.resolved = resolved;
    }

    public static Builder builder() {
        return new Builder();
    }

    @PrePersist
    protected void onCreate() {
        this.alertedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Plugin getPlugin() { return plugin; }
    public void setPlugin(Plugin plugin) { this.plugin = plugin; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public int getDislikesAtTrigger() { return dislikesAtTrigger; }
    public void setDislikesAtTrigger(int dislikesAtTrigger) { this.dislikesAtTrigger = dislikesAtTrigger; }

    public Instant getAlertedAt() { return alertedAt; }

    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }

    public static class Builder {
        private Long id;
        private Plugin plugin;
        private String reason;
        private int dislikesAtTrigger;
        private boolean resolved = false;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder plugin(Plugin plugin) { this.plugin = plugin; return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }
        public Builder dislikesAtTrigger(int dislikesAtTrigger) { this.dislikesAtTrigger = dislikesAtTrigger; return this; }
        public Builder resolved(boolean resolved) { this.resolved = resolved; return this; }

        public ModerationAlert build() {
            return new ModerationAlert(id, plugin, reason, dislikesAtTrigger, resolved);
        }
    }
}

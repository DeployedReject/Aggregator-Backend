package com.github.deployedreject.Aggregator_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "plugin_ratings",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_plugin_voter", columnNames = {"plugin_id", "voter_hash"})
    },
    indexes = {
        @Index(name = "idx_ratings_plugin_id", columnList = "plugin_id")
    }
)
public class PluginRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plugin_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ratings_plugin"))
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Plugin plugin;

    @Column(name = "voter_hash", length = 64, nullable = false)
    private String voterHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VoteType vote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PluginRating() {
    }

    public PluginRating(Long id, Plugin plugin, String voterHash, VoteType vote) {
        this.id = id;
        this.plugin = plugin;
        this.voterHash = voterHash;
        this.vote = vote;
    }

    public static Builder builder() {
        return new Builder();
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Plugin getPlugin() { return plugin; }
    public void setPlugin(Plugin plugin) { this.plugin = plugin; }

    public String getVoterHash() { return voterHash; }
    public void setVoterHash(String voterHash) { this.voterHash = voterHash; }

    public VoteType getVote() { return vote; }
    public void setVote(VoteType vote) { this.vote = vote; }

    public Instant getCreatedAt() { return createdAt; }

    public static class Builder {
        private Long id;
        private Plugin plugin;
        private String voterHash;
        private VoteType vote;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder plugin(Plugin plugin) { this.plugin = plugin; return this; }
        public Builder voterHash(String voterHash) { this.voterHash = voterHash; return this; }
        public Builder vote(VoteType vote) { this.vote = vote; return this; }

        public PluginRating build() {
            return new PluginRating(id, plugin, voterHash, vote);
        }
    }
}

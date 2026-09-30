package com.github.deployedreject.Aggregator_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "plugins",
    indexes = {
        @Index(name = "idx_plugins_channel_active", columnList = "channel, is_active"),
        @Index(name = "idx_plugins_likes_dislikes", columnList = "likes_count, dislikes_count")
    }
)
public class Plugin {

    @Id
    @Column(length = 64, nullable = false)
    private String id;

    @Column(length = 128, nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(length = 32, nullable = false)
    private String version = "1.0.0";

    @Column(length = 128, nullable = false)
    private String author = "anonymous";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PluginChannel channel = PluginChannel.NIGHTLY;

    @Column(name = "code_file_path", length = 512, nullable = false)
    private String codeFilePath;

    @Column(name = "likes_count", nullable = false)
    private int likesCount = 0;

    @Column(name = "dislikes_count", nullable = false)
    private int dislikesCount = 0;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Plugin() {
    }

    public Plugin(String id, String name, String description, String baseUrl, String version,
                  String author, PluginChannel channel, String codeFilePath, int likesCount,
                  int dislikesCount, boolean isActive) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.baseUrl = baseUrl;
        this.version = version != null ? version : "1.0.0";
        this.author = author != null ? author : "anonymous";
        this.channel = channel != null ? channel : PluginChannel.NIGHTLY;
        this.codeFilePath = codeFilePath;
        this.likesCount = likesCount;
        this.dislikesCount = dislikesCount;
        this.isActive = isActive;
    }

    public static Builder builder() {
        return new Builder();
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public PluginChannel getChannel() { return channel; }
    public void setChannel(PluginChannel channel) { this.channel = channel; }

    public String getCodeFilePath() { return codeFilePath; }
    public void setCodeFilePath(String codeFilePath) { this.codeFilePath = codeFilePath; }

    public int getLikesCount() { return likesCount; }
    public void setLikesCount(int likesCount) { this.likesCount = likesCount; }

    public int getDislikesCount() { return dislikesCount; }
    public void setDislikesCount(int dislikesCount) { this.dislikesCount = dislikesCount; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public static class Builder {
        private String id;
        private String name;
        private String description;
        private String baseUrl;
        private String version = "1.0.0";
        private String author = "anonymous";
        private PluginChannel channel = PluginChannel.NIGHTLY;
        private String codeFilePath;
        private int likesCount = 0;
        private int dislikesCount = 0;
        private boolean isActive = true;

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder author(String author) { this.author = author; return this; }
        public Builder channel(PluginChannel channel) { this.channel = channel; return this; }
        public Builder codeFilePath(String codeFilePath) { this.codeFilePath = codeFilePath; return this; }
        public Builder likesCount(int likesCount) { this.likesCount = likesCount; return this; }
        public Builder dislikesCount(int dislikesCount) { this.dislikesCount = dislikesCount; return this; }
        public Builder isActive(boolean isActive) { this.isActive = isActive; return this; }

        public Plugin build() {
            return new Plugin(id, name, description, baseUrl, version, author, channel, codeFilePath, likesCount, dislikesCount, isActive);
        }
    }
}

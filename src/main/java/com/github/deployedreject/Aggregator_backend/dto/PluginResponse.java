package com.github.deployedreject.Aggregator_backend.dto;

import com.github.deployedreject.Aggregator_backend.entity.Plugin;

import java.time.Instant;

public record PluginResponse(
    String id,
    String name,
    String description,
    String baseUrl,
    String version,
    String author,
    String channel,
    int likes,
    int dislikes,
    boolean isActive,
    Instant updatedAt
) {
    public static PluginResponse fromEntity(Plugin p) {
        return new PluginResponse(
            p.getId(),
            p.getName(),
            p.getDescription(),
            p.getBaseUrl(),
            p.getVersion(),
            p.getAuthor(),
            p.getChannel().name(),
            p.getLikesCount(),
            p.getDislikesCount(),
            p.isActive(),
            p.getUpdatedAt()
        );
    }
}

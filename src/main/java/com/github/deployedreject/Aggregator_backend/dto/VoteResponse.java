package com.github.deployedreject.Aggregator_backend.dto;

public record VoteResponse(
    String pluginId,
    int likes,
    int dislikes,
    String vote
) {
}

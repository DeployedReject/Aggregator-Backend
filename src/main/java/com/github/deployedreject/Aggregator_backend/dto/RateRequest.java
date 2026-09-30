package com.github.deployedreject.Aggregator_backend.dto;

import com.github.deployedreject.Aggregator_backend.entity.VoteType;
import jakarta.validation.constraints.NotNull;

public record RateRequest(
    @NotNull(message = "Vote must be either LIKE or DISLIKE")
    VoteType vote
) {
}

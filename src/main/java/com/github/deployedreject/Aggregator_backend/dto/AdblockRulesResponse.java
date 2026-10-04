package com.github.deployedreject.Aggregator_backend.dto;

import java.util.List;

public record AdblockRulesResponse(
    List<String> rules,
    int totalRules,
    String format,
    String updatedAt
) {}

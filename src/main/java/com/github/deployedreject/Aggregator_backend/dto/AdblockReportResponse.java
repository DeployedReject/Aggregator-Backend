package com.github.deployedreject.Aggregator_backend.dto;

public record AdblockReportResponse(
    String status,
    int added,
    int total
) {}

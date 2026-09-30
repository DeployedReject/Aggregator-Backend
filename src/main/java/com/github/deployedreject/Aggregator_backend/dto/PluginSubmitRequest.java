package com.github.deployedreject.Aggregator_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PluginSubmitRequest(
    @NotBlank
    @Pattern(regexp = "^[a-z0-9_-]{2,64}$", message = "ID must be lowercase alphanumeric, hyphens, or underscores (2-64 chars)")
    String id,

    @NotBlank
    @Size(max = 128)
    String name,

    String description,

    @NotBlank
    String baseUrl,

    @NotBlank
    @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "Version must follow SemVer (e.g. 1.0.0)")
    String version,

    String author,

    @NotBlank(message = "Plugin JavaScript code cannot be empty")
    String code
) {
}

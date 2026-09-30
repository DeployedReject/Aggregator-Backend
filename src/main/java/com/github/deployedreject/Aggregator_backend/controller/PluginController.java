package com.github.deployedreject.Aggregator_backend.controller;

import com.github.deployedreject.Aggregator_backend.dto.*;
import com.github.deployedreject.Aggregator_backend.entity.Plugin;
import com.github.deployedreject.Aggregator_backend.entity.PluginChannel;
import com.github.deployedreject.Aggregator_backend.service.GitSyncService;
import com.github.deployedreject.Aggregator_backend.service.PluginService;
import com.github.deployedreject.Aggregator_backend.service.RatingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/plugins")
public class PluginController {

    private final PluginService pluginService;
    private final RatingService ratingService;
    private final GitSyncService gitSyncService;

    public PluginController(PluginService pluginService, RatingService ratingService, GitSyncService gitSyncService) {
        this.pluginService = pluginService;
        this.ratingService = ratingService;
        this.gitSyncService = gitSyncService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<PluginResponse>> listPlugins(
        @RequestParam(defaultValue = "STABLE") PluginChannel channel,
        @RequestParam(required = false) String query,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "likesCount") String sortBy,
        @RequestParam(defaultValue = "desc") String direction
    ) {
        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), sort);

        Page<PluginResponse> pagedResult = pluginService.listPlugins(channel, query, pageable);
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=60")
            .body(PagedResponse.fromPage(pagedResult));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PluginResponse> getPlugin(@PathVariable String id) {
        Plugin plugin = pluginService.getPlugin(id);
        return ResponseEntity.ok(PluginResponse.fromEntity(plugin));
    }

    @GetMapping(value = "/{id}/download", produces = "application/javascript")
    public ResponseEntity<String> downloadPluginCode(@PathVariable String id) {
        String jsCode = pluginService.downloadPluginCode(id);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + id + ".js\"")
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
            .body(jsCode);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PluginResponse> submitPlugin(@Valid @RequestBody PluginSubmitRequest request) {
        PluginResponse response = pluginService.submitPlugin(request, "Community");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/rate")
    public ResponseEntity<VoteResponse> ratePlugin(
        @PathVariable String id,
        @Valid @RequestBody RateRequest request,
        HttpServletRequest httpRequest
    ) {
        String clientIp = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);

        VoteResponse response = ratingService.ratePlugin(id, request, clientIp, userAgent);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/internal/git/webhook")
    public ResponseEntity<String> triggerGitSync() {
        gitSyncService.pullAndSync();
        return ResponseEntity.ok("Git sync triggered successfully");
    }

    private String extractClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

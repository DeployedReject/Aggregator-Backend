package com.github.deployedreject.Aggregator_backend.controller;

import com.github.deployedreject.Aggregator_backend.config.AppProperties;
import com.github.deployedreject.Aggregator_backend.dto.PluginResponse;
import com.github.deployedreject.Aggregator_backend.entity.Plugin;
import com.github.deployedreject.Aggregator_backend.entity.PluginChannel;
import com.github.deployedreject.Aggregator_backend.repository.PluginRepository;
import com.github.deployedreject.Aggregator_backend.service.GitSyncService;
import com.github.deployedreject.Aggregator_backend.service.PluginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final PluginRepository pluginRepository;
    private final PluginService pluginService;
    private final GitSyncService gitSyncService;
    private final AppProperties appProperties;

    public AdminController(PluginRepository pluginRepository,
                           PluginService pluginService,
                           GitSyncService gitSyncService,
                           AppProperties appProperties) {
        this.pluginRepository = pluginRepository;
        this.pluginService = pluginService;
        this.gitSyncService = gitSyncService;
        this.appProperties = appProperties;
    }

    @GetMapping("/plugins")
    public ResponseEntity<List<PluginResponse>> listAllPlugins(
        @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        validateAdminToken(adminToken, authHeader);
        List<Plugin> all = pluginRepository.findAll(Sort.by(Sort.Direction.DESC, "likesCount"));
        return ResponseEntity.ok(all.stream().map(PluginResponse::fromEntity).toList());
    }

    @PutMapping("/plugins/{id}/channel")
    public ResponseEntity<PluginResponse> updateChannel(
        @PathVariable String id,
        @RequestParam PluginChannel channel,
        @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        validateAdminToken(adminToken, authHeader);
        Plugin updated = pluginService.updateChannel(id, channel);
        log.info("Admin updated plugin '{}' channel to {}", id, channel);
        return ResponseEntity.ok(PluginResponse.fromEntity(updated));
    }

    @PutMapping("/plugins/{id}/toggle-active")
    public ResponseEntity<PluginResponse> toggleActive(
        @PathVariable String id,
        @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        validateAdminToken(adminToken, authHeader);
        Plugin updated = pluginService.toggleActive(id);
        log.info("Admin toggled active status of plugin '{}' to {}", id, updated.isActive());
        return ResponseEntity.ok(PluginResponse.fromEntity(updated));
    }

    @DeleteMapping("/plugins/{id}")
    public ResponseEntity<Void> deletePlugin(
        @PathVariable String id,
        @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        validateAdminToken(adminToken, authHeader);
        pluginService.deletePlugin(id);
        log.info("Admin deleted plugin '{}'", id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/git/sync")
    public ResponseEntity<String> triggerGitSync(
        @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        validateAdminToken(adminToken, authHeader);
        log.info("Admin triggered manual Git pull and database sync");
        gitSyncService.pullAndSync();
        return ResponseEntity.ok("Git repository synchronized successfully");
    }

    private void validateAdminToken(String adminToken, String authHeader) {
        String configuredToken = appProperties.getSecurity().getAdminToken();
        if (configuredToken == null || configuredToken.isBlank()) {
            return;
        }

        String token = null;
        if (adminToken != null && !adminToken.isBlank()) {
            token = adminToken.trim();
        } else if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        }

        if (token == null || !token.equals(configuredToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing admin authentication token");
        }
    }
}

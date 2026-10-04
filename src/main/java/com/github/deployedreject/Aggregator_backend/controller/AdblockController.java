package com.github.deployedreject.Aggregator_backend.controller;

import com.github.deployedreject.Aggregator_backend.dto.AdblockRulesResponse;
import com.github.deployedreject.Aggregator_backend.service.GitSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/adblock")
public class AdblockController {

    private static final Logger log = LoggerFactory.getLogger(AdblockController.class);

    private final GitSyncService gitSyncService;

    public AdblockController(GitSyncService gitSyncService) {
        this.gitSyncService = gitSyncService;
    }

    @GetMapping("/rules")
    public ResponseEntity<AdblockRulesResponse> getRules() {
        try {
            List<String> rules = gitSyncService.readAdblockRules();
            AdblockRulesResponse response = new AdblockRulesResponse(
                rules,
                rules.size(),
                "adblock-filter",
                Instant.now().toString()
            );
            return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .body(response);
        } catch (IOException e) {
            log.error("Failed to read adblock rules from Git repo: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read adblock rules");
        }
    }
}

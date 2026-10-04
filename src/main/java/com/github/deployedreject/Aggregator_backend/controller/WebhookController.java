package com.github.deployedreject.Aggregator_backend.controller;

import com.github.deployedreject.Aggregator_backend.config.AppProperties;
import com.github.deployedreject.Aggregator_backend.service.GitSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final GitSyncService gitSyncService;
    private final AppProperties appProperties;

    public WebhookController(GitSyncService gitSyncService, AppProperties appProperties) {
        this.gitSyncService = gitSyncService;
        this.appProperties = appProperties;
    }

    /**
     * Webhook for Plugins repository updates.
     * Validates secret token, responds immediately with success, and triggers pull & sync in background.
     */
    @PostMapping("/plugins-sync")
    public ResponseEntity<Map<String, Object>> handlePluginsSync(
        @RequestHeader(value = "X-Webhook-Secret", required = false) String webhookSecretHeader,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        validateWebhookSecret(webhookSecretHeader, authHeader);

        log.info("Received GitHub push webhook for Aggregator-Plugins. Triggering background pull and sync.");
        CompletableFuture.runAsync(gitSyncService::pullAndSync);

        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Aggregator-Plugins webhook received successfully. Synchronization started in background.",
            "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Webhook for Backend repository deployments.
     * Triggers deploy.sh asynchronously in an isolated systemd unit and responds immediately.
     */
    @PostMapping("/backend-deploy")
    public ResponseEntity<Map<String, Object>> handleBackendDeploy(
        @RequestBody(required = false) Map<String, Object> body,
        @RequestHeader(value = "X-Deploy-Token", required = false) String deployTokenHeader,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        validateDeployToken(deployTokenHeader, authHeader);

        String downloadUrl = null;
        if (body != null && body.containsKey("downloadUrl")) {
            downloadUrl = String.valueOf(body.get("downloadUrl"));
        }

        log.info("Received GitHub push webhook for Aggregator-Backend. Triggering background deploy.sh. Artifact URL: {}", downloadUrl);

        try {
            File deployScript = new File("/home/ubuntu/Aggregator-Backend/deploy.sh");
            if (!deployScript.exists()) {
                deployScript = new File("./deploy.sh");
            }

            if (!deployScript.exists()) {
                log.error("deploy.sh not found at {}", deployScript.getAbsolutePath());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "message", "deploy.sh script not found on origin server"
                ));
            }

            String unitName = "aggregator-webhook-deploy-" + System.currentTimeMillis();
            ProcessBuilder pb;
            if (downloadUrl != null && !downloadUrl.isBlank()) {
                pb = new ProcessBuilder("sudo", "systemd-run", "--unit=" + unitName, "/bin/bash", deployScript.getAbsolutePath(), downloadUrl.trim());
            } else {
                pb = new ProcessBuilder("sudo", "systemd-run", "--unit=" + unitName, "/bin/bash", deployScript.getAbsolutePath());
            }
            pb.directory(deployScript.getParentFile());

            // Start isolated systemd unit in background
            pb.start();

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Deployment triggered successfully on origin server. Backup, artifact installation, and service reload started in background.",
                "unit", unitName,
                "timestamp", System.currentTimeMillis()
            ));

        } catch (Exception e) {
            log.error("Exception during deployment execution: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Failed to start deploy.sh: " + e.getMessage()
            ));
        }
    }

    private void validateWebhookSecret(String secretHeader, String authHeader) {
        String configuredSecret = appProperties.getGit().getSync().getWebhookSecret();
        if (configuredSecret == null || configuredSecret.isBlank()) {
            return;
        }

        String token = secretHeader;
        if (token == null && authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        }

        if (token == null || !token.trim().equals(configuredSecret.trim())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing webhook secret");
        }
    }

    private void validateDeployToken(String deployTokenHeader, String authHeader) {
        String configuredDeployToken = appProperties.getSecurity().getDeployToken();
        if (configuredDeployToken == null || configuredDeployToken.isBlank()) {
            return;
        }

        String token = deployTokenHeader;
        if (token == null && authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        }

        if (token == null || !token.trim().equals(configuredDeployToken.trim())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing deploy token");
        }
    }
}

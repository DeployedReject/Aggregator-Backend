package com.github.deployedreject.Aggregator_backend.controller;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import com.github.deployedreject.Aggregator_backend.config.AppProperties;
import com.github.deployedreject.Aggregator_backend.dto.PluginResponse;
import com.github.deployedreject.Aggregator_backend.dto.PluginSubmitRequest;
import com.github.deployedreject.Aggregator_backend.service.PluginService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/internal/llm")
public class LlmGatewayController {

  private static final String discord = System.getenv("discord");
  private static final Logger log = LoggerFactory.getLogger(LlmGatewayController.class);

  private final PluginService pluginService;
  private final AppProperties appProperties;

  public LlmGatewayController(PluginService pluginService, AppProperties appProperties) {
    this.pluginService = pluginService;
    this.appProperties = appProperties;
  }

  @PostMapping("/publish")
  public ResponseEntity<PluginResponse> publishLlmPlugin(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
      @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
      @Valid @RequestBody PluginSubmitRequest request) {
    validateToken(authHeader, apiKeyHeader);

    if (discord != null) {
      try {
        HttpClient.newHttpClient()
            .sendAsync(
                HttpRequest.newBuilder(URI.create(discord)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers
                        .ofString("{\"content\":\"New Plugin Published. Please review it.\"}"))
                    .build(),
                HttpResponse.BodyHandlers.discarding());
      } catch (Exception e) {
        log.error("Failed to send Discord notification", e);
      }
    }

    log.info("Received automated LLM synthesis upload for plugin: '{}'", request.id());

    PluginResponse response = pluginService.submitPlugin(request, "AI:Gemini");
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  private void validateToken(String authHeader, String apiKeyHeader) {
    String configuredToken = appProperties.getSecurity().getLlmApiToken();
    if (configuredToken == null || configuredToken.isBlank()) {
      return;
    }

    String providedToken = null;
    if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
      providedToken = apiKeyHeader.trim();
    } else if (authHeader != null && authHeader.startsWith("Bearer ")) {
      providedToken = authHeader.substring(7).trim();
    }

    if (providedToken == null || !providedToken.equals(configuredToken)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing LLM API authorization token");
    }
  }
}

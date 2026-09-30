package com.github.deployedreject.Aggregator_backend.service;

import com.github.deployedreject.Aggregator_backend.config.AppProperties;
import com.github.deployedreject.Aggregator_backend.entity.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class DiscordWebhookService {

    private static final Logger log = LoggerFactory.getLogger(DiscordWebhookService.class);

    private final RestClient restClient;
    private final AppProperties appProperties;

    public DiscordWebhookService(RestClient restClient, AppProperties appProperties) {
        this.restClient = restClient;
        this.appProperties = appProperties;
    }

    @Async
    public void sendModerationAlert(Plugin plugin, String reason, int dislikesAtTrigger) {
        String webhookUrl = appProperties.getDiscord().getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("Discord webhook URL is not configured. Skipping alert for plugin: {}", plugin.getId());
            return;
        }

        int totalVotes = plugin.getLikesCount() + plugin.getDislikesCount();
        double dislikePercent = totalVotes > 0 ? (plugin.getDislikesCount() * 100.0 / totalVotes) : 0.0;

        Map<String, Object> embed = Map.of(
            "title", "⚠️ Moderation Alert: " + plugin.getName() + " (`" + plugin.getId() + "`)",
            "description", "**Reason:** " + reason,
            "color", 0xE74C3C,
            "fields", List.of(
                Map.of("name", "Version", "value", plugin.getVersion(), "inline", true),
                Map.of("name", "Channel", "value", plugin.getChannel().name(), "inline", true),
                Map.of("name", "Author", "value", plugin.getAuthor(), "inline", true),
                Map.of("name", "Likes", "value", String.valueOf(plugin.getLikesCount()), "inline", true),
                Map.of("name", "Dislikes", "value", String.valueOf(plugin.getDislikesCount()), "inline", true),
                Map.of("name", "Dislike Ratio", "value", String.format("%.1f%%", dislikePercent), "inline", true),
                Map.of("name", "Base URL", "value", plugin.getBaseUrl(), "inline", false)
            ),
            "footer", Map.of("text", "Aggregator Plugin Registry • Review via Server TUI [D] to disable")
        );

        Map<String, Object> payload = Map.of(
            "content", "🚨 **[MODERATION ALERT]** A plugin has crossed the community flag threshold!",
            "embeds", List.of(embed)
        );

        try {
            restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();

            log.info("Moderation alert sent to Discord for plugin '{}'", plugin.getId());
        } catch (Exception e) {
            log.error("Failed to send Discord moderation alert for plugin '{}': {}", plugin.getId(), e.getMessage());
        }
    }
}

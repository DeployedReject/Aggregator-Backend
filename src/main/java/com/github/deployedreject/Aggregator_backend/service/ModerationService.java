package com.github.deployedreject.Aggregator_backend.service;

import com.github.deployedreject.Aggregator_backend.config.AppProperties;
import com.github.deployedreject.Aggregator_backend.entity.ModerationAlert;
import com.github.deployedreject.Aggregator_backend.entity.Plugin;
import com.github.deployedreject.Aggregator_backend.repository.ModerationAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModerationService {

    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);

    private final ModerationAlertRepository alertRepository;
    private final DiscordWebhookService discordWebhookService;
    private final AppProperties appProperties;

    public ModerationService(ModerationAlertRepository alertRepository,
                             DiscordWebhookService discordWebhookService,
                             AppProperties appProperties) {
        this.alertRepository = alertRepository;
        this.discordWebhookService = discordWebhookService;
        this.appProperties = appProperties;
    }

    @Transactional
    public void evaluatePluginDislikes(Plugin plugin) {
        if (alertRepository.existsByPluginIdAndResolvedFalse(plugin.getId())) {
            return;
        }

        int dislikes = plugin.getDislikesCount();
        int totalVotes = plugin.getLikesCount() + dislikes;
        var config = appProperties.getModeration();

        boolean totalDislikesExceeded = dislikes >= config.getDislikeThreshold();
        boolean ratioExceeded = false;
        double dislikeRatio = 0.0;

        if (totalVotes >= config.getMinVotesForRatio()) {
            dislikeRatio = (double) dislikes / totalVotes;
            ratioExceeded = dislikeRatio >= config.getDislikeRatioThreshold();
        }

        if (totalDislikesExceeded || ratioExceeded) {
            String reason;
            if (totalDislikesExceeded && ratioExceeded) {
                reason = String.format("Total dislikes (%d >= %d) AND dislike ratio (%.1f%% >= %.1f%%) exceeded",
                    dislikes, config.getDislikeThreshold(), dislikeRatio * 100, config.getDislikeRatioThreshold() * 100);
            } else if (totalDislikesExceeded) {
                reason = String.format("Total dislikes (%d >= %d) exceeded threshold",
                    dislikes, config.getDislikeThreshold());
            } else {
                reason = String.format("Dislike ratio (%.1f%% >= %.1f%%) exceeded with %d total votes",
                    dislikeRatio * 100, config.getDislikeRatioThreshold() * 100, totalVotes);
            }

            log.warn("Moderation trigger for plugin '{}': {}", plugin.getId(), reason);

            ModerationAlert alert = ModerationAlert.builder()
                .plugin(plugin)
                .reason(reason)
                .dislikesAtTrigger(dislikes)
                .resolved(false)
                .build();

            alertRepository.save(alert);
            discordWebhookService.sendModerationAlert(plugin, reason, dislikes);
        }
    }
}

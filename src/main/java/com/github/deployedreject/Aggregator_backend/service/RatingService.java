package com.github.deployedreject.Aggregator_backend.service;

import com.github.deployedreject.Aggregator_backend.dto.RateRequest;
import com.github.deployedreject.Aggregator_backend.dto.VoteResponse;
import com.github.deployedreject.Aggregator_backend.entity.Plugin;
import com.github.deployedreject.Aggregator_backend.entity.PluginRating;
import com.github.deployedreject.Aggregator_backend.entity.VoteType;
import com.github.deployedreject.Aggregator_backend.repository.PluginRatingRepository;
import com.github.deployedreject.Aggregator_backend.repository.PluginRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class RatingService {

    private static final Logger log = LoggerFactory.getLogger(RatingService.class);

    private final PluginRepository pluginRepository;
    private final PluginRatingRepository ratingRepository;
    private final ModerationService moderationService;

    public RatingService(PluginRepository pluginRepository,
                         PluginRatingRepository ratingRepository,
                         ModerationService moderationService) {
        this.pluginRepository = pluginRepository;
        this.ratingRepository = ratingRepository;
        this.moderationService = moderationService;
    }

    @Transactional
    public VoteResponse ratePlugin(String pluginId, RateRequest request, String clientIp, String userAgent) {
        Plugin plugin = pluginRepository.findById(pluginId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plugin not found: " + pluginId));

        if (!plugin.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot rate an inactive plugin");
        }

        String voterHash = computeVoterHash(clientIp, userAgent);
        VoteType newVote = request.vote();

        Optional<PluginRating> existingRatingOpt = ratingRepository.findByPluginIdAndVoterHash(pluginId, voterHash);

        int likeDelta = 0;
        int dislikeDelta = 0;

        if (existingRatingOpt.isPresent()) {
            PluginRating existingRating = existingRatingOpt.get();
            VoteType oldVote = existingRating.getVote();

            if (oldVote == newVote) {
                return new VoteResponse(plugin.getId(), plugin.getLikesCount(), plugin.getDislikesCount(), newVote.name());
            }

            existingRating.setVote(newVote);
            ratingRepository.save(existingRating);

            if (newVote == VoteType.LIKE) {
                likeDelta = +1;
                dislikeDelta = -1;
            } else {
                likeDelta = -1;
                dislikeDelta = +1;
            }
        } else {
            PluginRating newRating = PluginRating.builder()
                .plugin(plugin)
                .voterHash(voterHash)
                .vote(newVote)
                .build();
            ratingRepository.save(newRating);

            if (newVote == VoteType.LIKE) {
                likeDelta = +1;
            } else {
                dislikeDelta = +1;
            }
        }

        plugin.setLikesCount(Math.max(0, plugin.getLikesCount() + likeDelta));
        plugin.setDislikesCount(Math.max(0, plugin.getDislikesCount() + dislikeDelta));
        pluginRepository.save(plugin);

        if (newVote == VoteType.DISLIKE) {
            moderationService.evaluatePluginDislikes(plugin);
        }

        return new VoteResponse(plugin.getId(), plugin.getLikesCount(), plugin.getDislikesCount(), newVote.name());
    }

    public String computeVoterHash(String clientIp, String userAgent) {
        String raw = (clientIp != null ? clientIp : "127.0.0.1") + "|" + (userAgent != null ? userAgent : "generic-client");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}

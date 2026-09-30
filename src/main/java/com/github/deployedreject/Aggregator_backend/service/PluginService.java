package com.github.deployedreject.Aggregator_backend.service;

import com.github.deployedreject.Aggregator_backend.dto.PluginResponse;
import com.github.deployedreject.Aggregator_backend.dto.PluginSubmitRequest;
import com.github.deployedreject.Aggregator_backend.entity.Plugin;
import com.github.deployedreject.Aggregator_backend.entity.PluginChannel;
import com.github.deployedreject.Aggregator_backend.repository.PluginRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@Service
public class PluginService {

    private static final Logger log = LoggerFactory.getLogger(PluginService.class);

    private final PluginRepository pluginRepository;
    private final GitSyncService gitSyncService;

    public PluginService(PluginRepository pluginRepository, GitSyncService gitSyncService) {
        this.pluginRepository = pluginRepository;
        this.gitSyncService = gitSyncService;
    }

    @Transactional(readOnly = true)
    public Page<PluginResponse> listPlugins(PluginChannel channel, String query, Pageable pageable) {
        Page<Plugin> page;
        if (query != null && !query.isBlank()) {
            page = pluginRepository.searchPlugins(channel, true, query.trim(), pageable);
        } else {
            page = pluginRepository.findByChannelAndIsActive(channel, true, pageable);
        }
        return page.map(PluginResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Plugin getPlugin(String id) {
        return pluginRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plugin not found: " + id));
    }

    @Transactional(readOnly = true)
    public String downloadPluginCode(String id) {
        Plugin plugin = getPlugin(id);
        try {
            return gitSyncService.readPluginCode(plugin);
        } catch (IOException e) {
            log.error("Could not read plugin code for '{}': {}", id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Plugin code file missing or unreadable");
        }
    }

    @Transactional
    public PluginResponse submitPlugin(PluginSubmitRequest req, String defaultAuthor) {
        String id = req.id().toLowerCase().trim();

        Plugin plugin = pluginRepository.findById(id).orElseGet(() ->
            Plugin.builder()
                .id(id)
                .channel(PluginChannel.NIGHTLY)
                .likesCount(0)
                .dislikesCount(0)
                .isActive(true)
                .build()
        );

        plugin.setName(req.name().trim());
        plugin.setDescription(req.description() != null ? req.description().trim() : "");
        plugin.setBaseUrl(req.baseUrl().trim());
        plugin.setVersion(req.version().trim());
        plugin.setAuthor(req.author() != null && !req.author().isBlank() ? req.author().trim() : defaultAuthor);

        try {
            gitSyncService.savePluginCodeAndCommit(plugin, req.code());
        } catch (Exception e) {
            log.error("Failed to commit plugin code to Git for '{}': {}", id, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to commit plugin to Git repository: " + e.getMessage());
        }

        Plugin saved = pluginRepository.save(plugin);
        log.info("Plugin '{}' v{} registered on NIGHTLY channel", saved.getId(), saved.getVersion());
        return PluginResponse.fromEntity(saved);
    }

    @Transactional
    public Plugin updateChannel(String id, PluginChannel channel) {
        Plugin plugin = getPlugin(id);
        plugin.setChannel(channel);
        return pluginRepository.save(plugin);
    }

    @Transactional
    public Plugin toggleActive(String id) {
        Plugin plugin = getPlugin(id);
        plugin.setActive(!plugin.isActive());
        return pluginRepository.save(plugin);
    }
}

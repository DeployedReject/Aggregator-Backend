package com.github.deployedreject.Aggregator_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.deployedreject.Aggregator_backend.config.AppProperties;
import com.github.deployedreject.Aggregator_backend.entity.Plugin;
import com.github.deployedreject.Aggregator_backend.entity.PluginChannel;
import com.github.deployedreject.Aggregator_backend.repository.PluginRepository;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

@Service
public class GitSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitSyncService.class);

    private final AppProperties appProperties;
    private final PluginRepository pluginRepository;
    private final ObjectMapper objectMapper;

    private Git git;

    public GitSyncService(AppProperties appProperties, PluginRepository pluginRepository, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.pluginRepository = pluginRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void initializeGitRepo() {
        String repoPathStr = appProperties.getGit().getRepoPath();
        File repoDir = new File(repoPathStr);
        String remoteUrl = appProperties.getGit().getRemoteUrl();

        try {
            if (!repoDir.exists()) {
                repoDir.mkdirs();
            }

            File gitDir = new File(repoDir, ".git");
            if (gitDir.exists()) {
                log.info("Opening existing Git repository at '{}'", repoDir.getAbsolutePath());
                this.git = Git.open(repoDir);
            } else if (remoteUrl != null && !remoteUrl.isBlank()) {
                try {
                    log.info("Cloning remote Git repository '{}' into '{}'...", remoteUrl, repoDir.getAbsolutePath());
                    var cloneCmd = Git.cloneRepository()
                        .setURI(remoteUrl)
                        .setDirectory(repoDir)
                        .setBranch(appProperties.getGit().getBranch());

                    var creds = getCredentialsProvider();
                    if (creds != null) {
                        cloneCmd.setCredentialsProvider(creds);
                    }
                    this.git = cloneCmd.call();
                    log.info("Cloning complete.");
                } catch (Exception e) {
                    log.warn("Could not clone remote repository ({}) - initializing local Git repository at '{}'", e.getMessage(), repoDir.getAbsolutePath());
                    this.git = Git.init().setDirectory(repoDir).call();
                }
            } else {
                log.info("Initializing new local Git repository at '{}'", repoDir.getAbsolutePath());
                this.git = Git.init().setDirectory(repoDir).call();
            }

            syncFromDiskToDatabase();

        } catch (Exception e) {
            log.error("Failed to initialize Git repository at '{}': {}", repoPathStr, e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelayString = "${app.git.sync.polling-interval-ms:300000}")
    public void scheduledSync() {
        if (!appProperties.getGit().getSync().isPollingEnabled()) {
            return;
        }
        log.debug("Running scheduled Git pull and sync...");
        pullAndSync();
    }

    public synchronized void pullAndSync() {
        if (git == null) {
            log.warn("Git repository is not initialized. Skipping pull.");
            return;
        }

        String remoteUrl = appProperties.getGit().getRemoteUrl();
        if (remoteUrl == null || remoteUrl.isBlank()) {
            syncFromDiskToDatabase();
            return;
        }

        try {
            var pullCmd = git.pull();
            var creds = getCredentialsProvider();
            if (creds != null) {
                pullCmd.setCredentialsProvider(creds);
            }
            PullResult result = pullCmd.call();
            log.info("Git pull finished. Successful: {}", result.isSuccessful());
            syncFromDiskToDatabase();
        } catch (Exception e) {
            log.error("Git pull failed: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public synchronized void syncFromDiskToDatabase() {
        File repoDir = new File(appProperties.getGit().getRepoPath());
        File pluginsDir = new File(repoDir, "plugins");

        if (!pluginsDir.exists() || !pluginsDir.isDirectory()) {
            log.info("No 'plugins' directory found in Git repo at '{}'. Creating it.", pluginsDir.getAbsolutePath());
            pluginsDir.mkdirs();
            return;
        }

        File[] pluginDirs = pluginsDir.listFiles(File::isDirectory);
        if (pluginDirs == null) {
            return;
        }

        for (File dir : pluginDirs) {
            File manifestFile = new File(dir, "plugin.json");
            File codeFile = new File(dir, "index.js");

            if (!manifestFile.exists() || !codeFile.exists()) {
                continue;
            }

            try {
                Map<String, Object> manifest = objectMapper.readValue(manifestFile, Map.class);
                String id = (String) manifest.getOrDefault("id", dir.getName());
                String name = (String) manifest.getOrDefault("name", id);
                String description = (String) manifest.getOrDefault("description", "");
                String baseUrl = (String) manifest.getOrDefault("baseUrl", "");
                String version = (String) manifest.getOrDefault("version", "1.0.0");
                String author = (String) manifest.getOrDefault("author", "Community");
                String channelStr = (String) manifest.getOrDefault("channel", "NIGHTLY");
                PluginChannel channel = "STABLE".equalsIgnoreCase(channelStr) ? PluginChannel.STABLE : PluginChannel.NIGHTLY;

                String relativeCodePath = "plugins/" + dir.getName() + "/index.js";

                Optional<Plugin> existingOpt = pluginRepository.findById(id);
                if (existingOpt.isPresent()) {
                    Plugin existing = existingOpt.get();
                    existing.setName(name);
                    existing.setDescription(description);
                    existing.setBaseUrl(baseUrl);
                    existing.setVersion(version);
                    existing.setAuthor(author);
                    existing.setCodeFilePath(relativeCodePath);
                    pluginRepository.save(existing);
                    log.info("Synced updated plugin from Git: {}", id);
                } else {
                    Plugin newPlugin = Plugin.builder()
                        .id(id)
                        .name(name)
                        .description(description)
                        .baseUrl(baseUrl)
                        .version(version)
                        .author(author)
                        .channel(channel)
                        .codeFilePath(relativeCodePath)
                        .likesCount(0)
                        .dislikesCount(0)
                        .isActive(true)
                        .build();
                    pluginRepository.save(newPlugin);
                    log.info("Registered new plugin from Git: {}", id);
                }
            } catch (Exception e) {
                log.error("Failed to parse plugin in '{}': {}", dir.getAbsolutePath(), e.getMessage());
            }
        }
    }

    public synchronized void savePluginCodeAndCommit(Plugin plugin, String jsCode) throws IOException, GitAPIException {
        File repoDir = new File(appProperties.getGit().getRepoPath());
        File pluginFolder = new File(repoDir, "plugins/" + plugin.getId());
        if (!pluginFolder.exists()) {
            pluginFolder.mkdirs();
        }

        File manifestFile = new File(pluginFolder, "plugin.json");
        Map<String, Object> manifest = Map.of(
            "id", plugin.getId(),
            "name", plugin.getName(),
            "description", plugin.getDescription() != null ? plugin.getDescription() : "",
            "baseUrl", plugin.getBaseUrl(),
            "version", plugin.getVersion(),
            "author", plugin.getAuthor(),
            "channel", plugin.getChannel().name()
        );
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestFile, manifest);

        File codeFile = new File(pluginFolder, "index.js");
        Files.writeString(codeFile.toPath(), jsCode);

        plugin.setCodeFilePath("plugins/" + plugin.getId() + "/index.js");

        if (git != null) {
            String pattern = "plugins/" + plugin.getId();
            git.add().addFilepattern(pattern).call();
            git.commit()
                .setAuthor("Aggregator Registry", "registry@aggregator.local")
                .setCommitter("Aggregator Registry", "registry@aggregator.local")
                .setMessage("feat(plugin): update " + plugin.getId() + " to v" + plugin.getVersion())
                .call();
            log.info("Git commit created for plugin '{}'", plugin.getId());

            pushToRemoteAsync();
        }
    }

    public String readPluginCode(Plugin plugin) throws IOException {
        Path path = Paths.get(appProperties.getGit().getRepoPath(), plugin.getCodeFilePath());
        if (!Files.exists(path)) {
            throw new IOException("Plugin code file not found on disk: " + path);
        }
        return Files.readString(path);
    }

    @Async
    public void pushToRemoteAsync() {
        if (git == null) return;
        String remoteUrl = appProperties.getGit().getRemoteUrl();
        if (remoteUrl == null || remoteUrl.isBlank()) {
            log.debug("Remote Git URL is not configured. Skipping push.");
            return;
        }

        try {
            var pushCmd = git.push();
            var creds = getCredentialsProvider();
            if (creds != null) {
                pushCmd.setCredentialsProvider(creds);
            }
            pushCmd.call();
            log.info("Successfully pushed Git commits to remote '{}'", remoteUrl);
        } catch (Exception e) {
            log.error("Failed to push commits to remote Git repo: {}", e.getMessage(), e);
        }
    }

    private UsernamePasswordCredentialsProvider getCredentialsProvider() {
        String username = appProperties.getGit().getAuth().getUsername();
        String token = appProperties.getGit().getAuth().getToken();
        if (token != null && !token.isBlank()) {
            return new UsernamePasswordCredentialsProvider(username != null ? username : "git", token);
        }
        return null;
    }
}

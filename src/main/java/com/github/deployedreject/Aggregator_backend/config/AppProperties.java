package com.github.deployedreject.Aggregator_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Git git = new Git();
    private Discord discord = new Discord();
    private Moderation moderation = new Moderation();
    private Security security = new Security();

    public Git getGit() { return git; }
    public void setGit(Git git) { this.git = git; }

    public Discord getDiscord() { return discord; }
    public void setDiscord(Discord discord) { this.discord = discord; }

    public Moderation getModeration() { return moderation; }
    public void setModeration(Moderation moderation) { this.moderation = moderation; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public static class Git {
        private String repoPath = "./data/plugins-repo";
        private String remoteUrl = "";
        private String branch = "main";
        private Auth auth = new Auth();
        private Sync sync = new Sync();

        public String getRepoPath() { return repoPath; }
        public void setRepoPath(String repoPath) { this.repoPath = repoPath; }

        public String getRemoteUrl() { return remoteUrl; }
        public void setRemoteUrl(String remoteUrl) { this.remoteUrl = remoteUrl; }

        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }

        public Auth getAuth() { return auth; }
        public void setAuth(Auth auth) { this.auth = auth; }

        public Sync getSync() { return sync; }
        public void setSync(Sync sync) { this.sync = sync; }

        public static class Auth {
            private String username = "";
            private String token = "";

            public String getUsername() { return username; }
            public void setUsername(String username) { this.username = username; }

            public String getToken() { return token; }
            public void setToken(String token) { this.token = token; }
        }

        public static class Sync {
            private boolean pollingEnabled = true;
            private long pollingIntervalMs = 300000;
            private String webhookSecret = "dev-secret-key";

            public boolean isPollingEnabled() { return pollingEnabled; }
            public void setPollingEnabled(boolean pollingEnabled) { this.pollingEnabled = pollingEnabled; }

            public long getPollingIntervalMs() { return pollingIntervalMs; }
            public void setPollingIntervalMs(long pollingIntervalMs) { this.pollingIntervalMs = pollingIntervalMs; }

            public String getWebhookSecret() { return webhookSecret; }
            public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
        }
    }

    public static class Discord {
        private String webhookUrl = "";

        public String getWebhookUrl() { return webhookUrl; }
        public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    }

    public static class Moderation {
        private int dislikeThreshold = 10;
        private double dislikeRatioThreshold = 0.40;
        private int minVotesForRatio = 5;

        public int getDislikeThreshold() { return dislikeThreshold; }
        public void setDislikeThreshold(int dislikeThreshold) { this.dislikeThreshold = dislikeThreshold; }

        public double getDislikeRatioThreshold() { return dislikeRatioThreshold; }
        public void setDislikeRatioThreshold(double dislikeRatioThreshold) { this.dislikeRatioThreshold = dislikeRatioThreshold; }

        public int getMinVotesForRatio() { return minVotesForRatio; }
        public void setMinVotesForRatio(int minVotesForRatio) { this.minVotesForRatio = minVotesForRatio; }
    }

    public static class Security {
        private String llmApiToken = "dev-llm-token-12345";
        private String adminToken = "aggregator-admin-token";

        public String getLlmApiToken() { return llmApiToken; }
        public void setLlmApiToken(String llmApiToken) { this.llmApiToken = llmApiToken; }

        public String getAdminToken() { return adminToken; }
        public void setAdminToken(String adminToken) { this.adminToken = adminToken; }
    }
}

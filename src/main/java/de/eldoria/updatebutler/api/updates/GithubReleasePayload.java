package de.eldoria.updatebutler.api.updates;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class GithubReleasePayload {
    private String action;
    private GitRelease release;

    public String getAction() {
        return this.action;
    }

    public GitRelease getRelease() {
        return this.release;
    }

    public class GitRelease {
        @JsonProperty("tag_name")
        String tag;
        String name;
        String body;
        boolean draft;
        boolean prerelease;
        @JsonProperty("created_at")
        String createdAt;
        List<Asset> assets;

        public String getTag() {
            return this.tag;
        }

        public String getName() {
            return this.name;
        }

        public String getBody() {
            return this.body;
        }

        public boolean isDraft() {
            return this.draft;
        }

        public boolean isPrerelease() {
            return this.prerelease;
        }

        public String getCreatedAt() {
            return this.createdAt;
        }

        public List<Asset> getAssets() {
            return this.assets;
        }

        public class Asset {
            @JsonProperty("browser_download_url")
            String url;
            String name;

            public String getUrl() {
                return this.url;
            }

            public String getName() {
                return this.name;
            }
        }
    }
}

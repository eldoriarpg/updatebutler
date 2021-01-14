package de.eldoria.updatebutler.api.updates;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.List;

@Getter
public class GithubReleasePayload {
    private String action;
    private GitRelease release;

    @Getter
    public class GitRelease {
        @SerializedName("tag_name")
        String tag;
        String name;
        String body;
        boolean draft;
        boolean prerelease;
        @SerializedName("created_at")
        String createdAt;
        List<Asset> assets;

        @Getter
        public class Asset {
            @SerializedName("browser_download_url")
            String url;
            String name;
        }
    }
}

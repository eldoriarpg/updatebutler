package de.eldoria.updatebutler.api;

import lombok.Getter;

@Getter
public class UpdateCheckPayload {
    private int applicationId;
    private String version;
    private boolean allowDevBuilds;

    public String getVersion() {
        return version.replace("_", " ");
    }
}

package de.eldoria.updatebutler.api.updates;

import lombok.Getter;

@Getter
public class UpdateCheckPayload {
    private int applicationId;
    private String version;
    private boolean allowDevBuilds;
}

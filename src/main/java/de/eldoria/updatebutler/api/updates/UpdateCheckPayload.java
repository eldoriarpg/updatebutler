package de.eldoria.updatebutler.api.updates;

public class UpdateCheckPayload {
    private int applicationId;
    private String version;
    private boolean allowDevBuilds;

    public int getApplicationId() {
        return this.applicationId;
    }

    public String getVersion() {
        return this.version;
    }

    public boolean isAllowDevBuilds() {
        return this.allowDevBuilds;
    }
}

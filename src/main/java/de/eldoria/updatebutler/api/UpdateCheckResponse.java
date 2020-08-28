package de.eldoria.updatebutler.api;

public class UpdateCheckResponse {
    private final boolean newVersionAvailable;
    private final String latestVersion;
    private final String hash;

    public UpdateCheckResponse(boolean newVersionAvailable, String latestVersion, String hash) {
        this.newVersionAvailable = newVersionAvailable;
        this.latestVersion = latestVersion.replace(" ", "_");
        this.hash = hash;
    }
}

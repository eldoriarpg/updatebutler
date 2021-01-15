package de.eldoria.updatebutler.api.debug;

public class DebugResponse {
    private String hash;
    private String deletionHash;

    public DebugResponse(String hash, String deletionHash) {
        this.hash = hash;
        this.deletionHash = deletionHash;
    }
}

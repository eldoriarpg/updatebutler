package de.eldoria.updatebutler.api.debug;

public class DebugResponse {
    private final String hash;
    private final String deletionHash;

    public DebugResponse(String hash, String deletionHash) {
        this.hash = hash;
        this.deletionHash = deletionHash;
    }
}

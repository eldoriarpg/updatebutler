package de.eldoria.updatebutler.api.debug.data;

import lombok.Getter;

@Getter
public class ConfigDumpData {
    protected String path;
    protected String content;

    public ConfigDumpData(String path, String content) {
        this.path = path;
        this.content = content;
    }

    @Override
    public String toString() {
        return "Path: " + path + "\n\n" + content;
    }
}

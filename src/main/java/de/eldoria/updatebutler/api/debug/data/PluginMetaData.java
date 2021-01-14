package de.eldoria.updatebutler.api.debug.data;

import lombok.Getter;

@Getter
public class PluginMetaData {
    protected String name;
    protected String version;
    protected boolean enabled;
    protected String main;
    protected String[] authors;
    protected String[] loadBefore;
    protected String[] dependencies;
    protected String[] softDependencies;
    protected String[] provides;

    public PluginMetaData(String name, String version, boolean enabled, String main, String[] authors, String[] loadBefore, String[] dependencies, String[] softDependencies, String[] provides) {
        this.name = name;
        this.version = version;
        this.enabled = enabled;
        this.main = main;
        this.authors = authors;
        this.loadBefore = loadBefore;
        this.dependencies = dependencies;
        this.softDependencies = softDependencies;
        this.provides = provides;
    }

    @Override
    public String toString() {
        return toString(0);
    }

    public String toString(int offset) {
        String off = " ".repeat(offset);
        return off + name + ":" + "\n" +
                off + "  " + "version: " + version + "\n" +
                off + "  " + "enabled: " + enabled + "\n" +
                off + "  " + "main: " + main + "\n" +
                off + "  " + "authors: [" + String.join(", ", authors) + "]" + "\n" +
                off + "  " + "loadBefore: [" + String.join(", ", loadBefore) + "]" + "\n" +
                off + "  " + "dependencies: [" + String.join(", ", dependencies) + "]" + "\n" +
                off + "  " + "softDependencies: [" + String.join(", ", softDependencies) + "]" + "\n" +
                off + "  " + "provides: [" + String.join(", ", provides) + "]";

    }
}

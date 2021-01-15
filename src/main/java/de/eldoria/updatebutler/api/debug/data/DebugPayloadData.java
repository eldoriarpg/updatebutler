package de.eldoria.updatebutler.api.debug.data;

import lombok.Data;

import java.util.Arrays;
import java.util.stream.Collectors;

@Data
public class DebugPayloadData {
    protected PluginMetaData pluginMeta;
    protected ServerMetaData serverMeta;
    protected EntryData[] additionalPluginMeta;
    protected String latestLog;
    protected EntryData[] configDumps;
    private final int v = 1;

    public DebugPayloadData(PluginMetaData pluginMeta, ServerMetaData serverMeta, EntryData[] additionalPluginMeta, String latestLog, EntryData[] configDumps) {
        this.pluginMeta = pluginMeta;
        this.serverMeta = serverMeta;
        this.additionalPluginMeta = additionalPluginMeta;
        this.latestLog = latestLog;
        this.configDumps = configDumps;
    }

    @Override
    public String toString() {
        return "Paste created for " + pluginMeta.name + " - " + pluginMeta.version
                + "\n\nPlugin Meta:\n"
                + pluginMeta.toString()
                + "\n\nServer Meta:\n"
                + serverMeta.toString()
                + Arrays.stream(additionalPluginMeta).map(c -> "\n\n" + c.getName() + "\n" + c.getContent()).collect(Collectors.joining())
                + "\n\nLatest Log:\n"
                + latestLog
                + "\n\n"
                + Arrays.stream(configDumps)
                .map(c -> "Path: " + c.getName() + "\n" + c.getContent())
                .collect(Collectors.joining("\n\n"));
    }
}

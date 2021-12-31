package de.eldoria.updatebutler.api.debug.data;

import java.util.Arrays;
import java.util.stream.Collectors;

public class DebugPayloadData {
    protected PluginMetaData pluginMeta;
    protected ServerMetaData serverMeta;
    protected EntryData[] additionalPluginMeta;
    protected LogData latestLog;
    protected EntryData[] configDumps;
    private final int v = 1;

    public DebugPayloadData(PluginMetaData pluginMeta, ServerMetaData serverMeta, EntryData[] additionalPluginMeta, LogData latestLog, EntryData[] configDumps) {
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

    public PluginMetaData pluginMeta() {
        return this.pluginMeta;
    }

    public ServerMetaData serverMeta() {
        return this.serverMeta;
    }

    public EntryData[] additionalPluginMeta() {
        return this.additionalPluginMeta;
    }

    public LogData latestLog() {
        return this.latestLog;
    }

    public EntryData[] configDumps() {
        return this.configDumps;
    }

    public int version() {
        return this.v;
    }
}

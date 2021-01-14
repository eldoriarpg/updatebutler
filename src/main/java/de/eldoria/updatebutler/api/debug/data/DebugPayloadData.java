package de.eldoria.updatebutler.api.debug.data;

import lombok.Getter;

import java.util.Arrays;
import java.util.stream.Collectors;

@Getter
public class DebugPayloadData {
    private final int v = 1;
    protected PluginMetaData pluginMeta;
    protected ServerMetaData serverMeta;
    protected String additionalPluginMeta;
    protected String latestLog;
    protected ConfigDumpData[] configDumps;

    public DebugPayloadData(PluginMetaData pluginMeta, ServerMetaData serverMeta, String additionalPluginMeta, String latestLog, ConfigDumpData[] configDumps) {
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
                + "\n\nAdditional Plugin Meta:\n"
                + (additionalPluginMeta == null ? "none" : additionalPluginMeta)
                + "\n\nLatest Log:\n"
                + latestLog
                + "\n\n"
                + Arrays.stream(configDumps)
                .map(c -> "Path: " + c.getPath() + "\n" + c.getContent())
                .collect(Collectors.joining("\n\n"));
    }
}

package de.eldoria.updatebutler.api.debug;

import de.eldoria.updatebutler.api.debug.data.ConfigDumpData;
import de.eldoria.updatebutler.api.debug.data.DebugPayloadData;
import de.eldoria.updatebutler.api.debug.data.PluginMetaData;
import de.eldoria.updatebutler.api.debug.data.ServerMetaData;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

@Slf4j
public class DebugPayload extends DebugPayloadData {
    private static final String HTML = "<!DOCTYPE html><html><head><style>pre { white-space: pre-wrap; }</style></head><body><pre>{content}</pre></body></html>";
    private static final Pattern IP = Pattern.compile("(([0-9]{1,3}\\.){3}[0-9]{1,3}(:[0-9]{1,5})?)");

    public DebugPayload(PluginMetaData pluginMeta, ServerMetaData serverMeta, String additionalPluginMeta,
                        String latestLog, ConfigDumpData[] configDumps) {
        super(pluginMeta,
                serverMeta,
                additionalPluginMeta,
                latestLog.replaceAll(IP.pattern(), "172.168.192.1"),
                configDumps);
    }

    public String toHtml() {
        return HTML.replace("{content}", super.toString());
    }
}

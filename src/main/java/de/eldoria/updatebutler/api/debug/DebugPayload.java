package de.eldoria.updatebutler.api.debug;

import de.eldoria.updatebutler.api.debug.data.DebugPayloadData;
import de.eldoria.updatebutler.api.debug.data.EntryData;
import de.eldoria.updatebutler.api.debug.data.LogData;
import de.eldoria.updatebutler.api.debug.data.PluginMetaData;
import de.eldoria.updatebutler.api.debug.data.ServerMetaData;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.util.regex.Pattern;

import static org.slf4j.LoggerFactory.getLogger;

public class DebugPayload extends DebugPayloadData {
    private static final Pattern IP = Pattern.compile("(([0-9]{1,3}\\.){3}[0-9]{1,3}(:[0-9]{1,5})?)");

    public DebugPayload(PluginMetaData pluginMeta, ServerMetaData serverMeta, EntryData[] additionalPluginMeta,
                        LogData logData, EntryData[] configDumps) {
        super(pluginMeta,
                serverMeta,
                additionalPluginMeta,
                logData,
                configDumps);
    }
}

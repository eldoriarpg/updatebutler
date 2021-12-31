package de.eldoria.updatebutler.database;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.chojo.sqlutil.base.QueryFactoryHolder;
import de.chojo.sqlutil.exceptions.ExceptionTransformer;
import de.chojo.sqlutil.wrapper.QueryBuilderConfig;
import de.eldoria.updatebutler.api.debug.DebugPayload;
import de.eldoria.updatebutler.api.debug.DebugResponse;
import de.eldoria.updatebutler.api.debug.data.EntryData;
import de.eldoria.updatebutler.api.debug.data.LogData;
import de.eldoria.updatebutler.api.debug.data.PluginMetaData;
import de.eldoria.updatebutler.api.debug.data.ServerMetaData;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;

import javax.sql.DataSource;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

public class DebugData extends QueryFactoryHolder {

    private final ObjectMapper mapper = new ObjectMapper();
    // private final Gson gson = new GsonBuilder().serializeNulls().create();
    private static final Logger log = getLogger(DebugData.class);

    public DebugData(DataSource source) {
        super(source, QueryBuilderConfig.builder()
                .withExceptionHandler(e -> log.error(ExceptionTransformer.prettyException("SQL Exception occured", e), e))
                .build());
    }

    public Optional<DebugResponse> submitDebug(DebugPayload payload) throws JsonProcessingException {
        var read = DigestUtils.sha1Hex(String.valueOf(System.nanoTime()));
        var delete = DigestUtils.sha1Hex(System.nanoTime() + read);
        var debugId = builder(Integer.class)
                .query("INSERT INTO debug(read_hash, deletion_hash) VALUES(?,?) RETURNING id")
                .paramsBuilder(param -> param.setString(read).setString(delete))
                .readRow(r -> r.getInt(1))
                .firstSync();

        if (debugId.isEmpty()) {
            throw new IllegalStateException("No id for debug was created.");
        }
        var plugin = mapper.writeValueAsString(payload.pluginMeta());
        var server = mapper.writeValueAsString(payload.serverMeta());
        var log = mapper.writeValueAsString(payload.latestLog());
        var additional = mapper.writeValueAsString(payload.additionalPluginMeta());
        var builder = builder().query("INSERT INTO debug_data(debug_id, plugin, server_meta, log_meta, additional) VALUES (?,?,?,?,?)")
                .paramsBuilder(p -> p.setInt(debugId.get()).setString(plugin).setString(server).setString(log).setString(additional));

        for (var configDump : payload.configDumps()) {
            builder.append().query("INSERT INTO debug_configs(debug_id, config_path, content) VALUES (?,?,?)")
                    .paramsBuilder(p -> p.setInt(debugId.get()).setString(configDump.getName()).setString(configDump.getContent()));
        }

        builder.insert().execute();

        return Optional.of(new DebugResponse(read, delete));
    }

    public Optional<Integer> getIdFromDeletionHash(String hash) {
        return builder(Integer.class).query("SELECT id FROM debug WHERE deletion_hash = ?")
                .paramsBuilder(p -> p.setString(hash))
                .readRow(r -> r.getInt(1))
                .firstSync();
    }

    public Optional<Integer> getIdFromHash(String hash) {
        return builder(Integer.class).query("SELECT id FROM debug WHERE deletion_hash = ?")
                .paramsBuilder(p -> p.setString(hash))
                .readRow(r -> r.getInt(1))
                .firstSync();
    }

    public Optional<DebugPayload> loadDebug(int id) {
        var configs = builder(EntryData.class).query("SELECT config_path, content FROM debug_configs WHERE debug_id = ?")
                .paramsBuilder(p -> p.setInt(id))
                .readRow(r -> new EntryData(r.getString("config_path"), r.getString("content")))
                .allSync();

        return builder(DebugPayload.class).query("SELECT log_meta, plugin, server_meta, additional FROM debug_data WHERE debug_id = ?")
                .paramsBuilder(p -> p.setInt(id))
                .readRow(r -> {
                    PluginMetaData plugin;
                    LogData logData;
                    ServerMetaData server;
                    EntryData[] additional;
                    try {
                        plugin = mapper.readValue(r.getString("plugin"), PluginMetaData.class);
                        server = mapper.readValue(r.getString("server_meta"), ServerMetaData.class);
                        additional = mapper.readValue(r.getString("additional"), EntryData[].class);
                        logData = mapper.readValue(r.getString("log_meta"), LogData.class);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to parse json", e);
                        return null;
                    }
                    var payload = new DebugPayload(
                            plugin,
                            server,
                            additional,
                            logData,
                            configs.toArray(EntryData[]::new));
                    return payload;
                }).firstSync();
    }

    public void cleanUp() {
        builder().query("DELETE FROM debug WHERE timestamp < NOW() - '30 DAY'::interval")
                .emptyParams()
                .delete()
                .execute();
    }

    public void deleteDebug(int id) {
        builder().query("DELETE FROM debug WHERE id = ?")
                .paramsBuilder(p -> p.setInt(id))
                .delete()
                .execute();
    }
}

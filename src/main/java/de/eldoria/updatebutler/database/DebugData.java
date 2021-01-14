package de.eldoria.updatebutler.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.eldoria.updatebutler.api.debug.DebugPayload;
import de.eldoria.updatebutler.api.debug.data.ConfigDumpData;
import de.eldoria.updatebutler.api.debug.data.PluginMetaData;
import de.eldoria.updatebutler.api.debug.data.ServerMetaData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.Pair;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class DebugData {

    private final DataSource source;
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    public DebugData(DataSource source) {
        this.source = source;
    }

    public Optional<Pair<Integer, String>> submitDebug(DebugPayload payload) {
        int id;
        String hash;
        try (Connection conn = source.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT into debugs(hash) VALUES (?)");
             PreparedStatement stmt2 = conn.prepareStatement(
                     "SELECT LAST_INSERT_ID();")) {
            hash = DigestUtils.shaHex(String.valueOf(System.nanoTime()));
            stmt.setString(1, hash);
            stmt.execute();
            ResultSet rs = stmt2.executeQuery();
            if (rs.next()) {
                id = rs.getInt(1);
            } else {
                throw new IllegalStateException("No id for debug was created.");
            }
        } catch (SQLException e) {
            log.error("Could not register new debug.", e);
            return Optional.empty();
        }

        try (Connection conn = source.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT into debug_data(id, plugin_meta, server_meta, log, additional_meta) VALUES (?,?,?,?,?)")) {
            stmt.setInt(1, id);
            stmt.setString(2, gson.toJson(payload.getPluginMeta()));
            stmt.setString(3, gson.toJson(payload.getServerMeta()));
            stmt.setString(4, payload.getLatestLog());
            stmt.setString(5, payload.getAdditionalPluginMeta());
            stmt.execute();
        } catch (SQLException e) {
            log.error("Could not register new debug.", e);
            return Optional.empty();
        }

        for (ConfigDumpData configDump : payload.getConfigDumps()) {
            try (Connection conn = source.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT into debug_configs(debug_id, path, config) VALUES (?,?,?)")) {
                stmt.setInt(1, id);
                stmt.setString(2, configDump.getPath());
                stmt.setString(3, configDump.getContent());
                stmt.execute();
            } catch (SQLException e) {
                log.error("Could not register new debug.", e);
                return Optional.empty();
            }
        }
        return Optional.of(Pair.of(id, hash));
    }

    public Optional<DebugPayload> loadDebug(String hash) {
        int id;
        try (Connection conn = source.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "Select id from debugs where hash = ?")) {
            stmt.setString(1, hash);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                id = rs.getInt(1);
            } else {
                return Optional.empty();
            }
        } catch (SQLException e) {
            log.error("Could not register new debug.", e);
            return Optional.empty();
        }

        PluginMetaData pluginMeta;
        String latestLog;
        ServerMetaData serverMeta;
        String additionalMeta;

        try (Connection conn = source.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "Select log, plugin_meta, server_meta, additional_meta from debug_data where id = ?")) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                pluginMeta = gson.fromJson(rs.getString("plugin_meta"), PluginMetaData.class);
                serverMeta = gson.fromJson(rs.getString("server_meta"), ServerMetaData.class);
                additionalMeta = rs.getString("additional_meta");
                latestLog = rs.getString("log");
            } else {
                return Optional.empty();
            }
        } catch (SQLException e) {
            log.error("Could not register new debug.", e);
            return Optional.empty();
        }

        List<ConfigDumpData> configs = new ArrayList<>();

        try (Connection conn = source.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "Select path, config from debug_configs where debug_id = ?")) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                configs.add(new ConfigDumpData(rs.getString("path"), rs.getString("config")));
            }
        } catch (SQLException e) {
            log.error("Could not register new debug.", e);
            return Optional.empty();
        }
        return Optional.of(new DebugPayload(pluginMeta, serverMeta, additionalMeta, latestLog, configs.toArray(new ConfigDumpData[0])));
    }

    public void cleanUp() {
        List<Integer> expired = new ArrayList<>();
        try (Connection conn = source.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "Select id from debugs where timestamp < now() - INTERVAL 30 DAY")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                expired.add(rs.getInt(1));
            }
        } catch (SQLException e) {
            log.error("Could not register new debug.", e);
            return;
        }

        for (int id : expired) {
            try (Connection conn = source.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "Delete from debugs where id = ?")) {
                stmt.setInt(1, id);
                stmt.executeUpdate();
            } catch (SQLException e) {
                log.error("Could not delete debug.", e);
                return;
            }
            try (Connection conn = source.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "Delete from debug_data where id = ?")) {
                stmt.setInt(1, id);
                stmt.executeUpdate();
            } catch (SQLException e) {
                log.error("Could not delete meta data.", e);
                return;
            }
            try (Connection conn = source.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "Delete from debug_configs where debug_id = ?")) {
                stmt.setInt(1, id);
                stmt.executeUpdate();
            } catch (SQLException e) {
                log.error("Could not delete configs.", e);
                return;
            }
        }
    }
}

package de.eldoria.updatebutler.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.eldoria.updatebutler.api.debug.DebugPayload;
import de.eldoria.updatebutler.api.debug.DebugResponse;
import de.eldoria.updatebutler.api.debug.data.EntryData;
import de.eldoria.updatebutler.api.debug.data.LogData;
import de.eldoria.updatebutler.api.debug.data.PluginMetaData;
import de.eldoria.updatebutler.api.debug.data.ServerMetaData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

@Slf4j
public class DebugData {

    private final DataSource source;
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    public DebugData(DataSource source) {
        this.source = source;
    }

    public Optional<DebugResponse> submitDebug(DebugPayload payload) {
        int id;
        String hash;
        String deletionHash;
        try (Connection conn = source.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT into debugs(hash, deletion_hash) VALUES (?,?)");
             PreparedStatement stmt2 = conn.prepareStatement(
                     "SELECT LAST_INSERT_ID();")) {
            hash = DigestUtils.shaHex(String.valueOf(System.nanoTime()));
            deletionHash = DigestUtils.shaHex(System.nanoTime() + hash);
            stmt.setString(1, hash);
            stmt.setString(2, deletionHash);
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
                     "INSERT into debug_data(id, plugin_meta, server_meta, log_meta, additional_data) VALUES (?,?,?,?,?)")) {
            stmt.setInt(1, id);
            stmt.setString(2, gson.toJson(payload.getPluginMeta()));
            stmt.setString(3, gson.toJson(payload.getServerMeta()));
            stmt.setString(4, gson.toJson(payload.getLatestLog()));
            stmt.setString(5, gson.toJson(payload.getAdditionalPluginMeta()));
            stmt.execute();
        } catch (SQLException e) {
            log.error("Could not register new debug.", e);
            return Optional.empty();
        }

        for (EntryData configDump : payload.getConfigDumps()) {
            try (Connection conn = source.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT into debug_configs(debug_id, path, config) VALUES (?,?,?)")) {
                stmt.setInt(1, id);
                stmt.setString(2, configDump.getName());
                stmt.setString(3, configDump.getContent());
                stmt.execute();
            } catch (SQLException e) {
                log.error("Could not register new debug.", e);
                return Optional.empty();
            }
        }
        return Optional.of(new DebugResponse(hash, deletionHash));
    }

    public OptionalInt getIdFromDeletionHash(String hash) {
        try (Connection conn = source.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "Select id from debugs where deletion_hash = ?")) {
            stmt.setString(1, hash);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return OptionalInt.of(rs.getInt(1));
            } else {
                return OptionalInt.empty();
            }
        } catch (SQLException e) {
            log.error("Could not register new debug.", e);
            return OptionalInt.empty();
        }
    }

    public OptionalInt getIdFromHash(String hash) {
        try (Connection conn = source.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "Select id from debugs where hash = ?")) {
            stmt.setString(1, hash);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return OptionalInt.of(rs.getInt(1));
            } else {
                return OptionalInt.empty();
            }
        } catch (SQLException e) {
            log.error("Could not register new debug.", e);
            return OptionalInt.empty();
        }
    }

    public Optional<DebugPayload> loadDebug(Integer id) {
        PluginMetaData pluginMeta;
        LogData latestLog;
        ServerMetaData serverMeta;
        EntryData[] additionalMeta;

        try (Connection conn = source.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "Select log_meta, plugin_meta, server_meta, additional_data from debug_data where id = ?")) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                pluginMeta = gson.fromJson(rs.getString("plugin_meta"), PluginMetaData.class);
                serverMeta = gson.fromJson(rs.getString("server_meta"), ServerMetaData.class);
                additionalMeta = gson.fromJson(rs.getString("additional_data"), EntryData[].class);
                latestLog = gson.fromJson(rs.getString("log_meta"), LogData.class);
            } else {
                return Optional.empty();
            }
        } catch (SQLException e) {
            log.error("Could not register new debug.", e);
            return Optional.empty();
        }

        List<EntryData> configs = new ArrayList<>();

        try (Connection conn = source.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "Select path, config from debug_configs where debug_id = ?")) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                configs.add(new EntryData(rs.getString("path"), rs.getString("config")));
            }
        } catch (SQLException e) {
            log.error("Could not register new debug.", e);
            return Optional.empty();
        }
        return Optional.of(new DebugPayload(pluginMeta, serverMeta, additionalMeta, latestLog, configs.toArray(new EntryData[0])));
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
            deleteDebug(id);
        }
    }

    public void deleteDebug(int id) {
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

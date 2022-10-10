package de.eldoria.updatebutler.database;

import com.google.common.hash.Hashing;
import de.chojo.sqlutil.base.QueryFactoryHolder;
import de.chojo.sqlutil.conversion.ArrayConverter;
import de.chojo.sqlutil.wrapper.QueryBuilderConfig;
import de.eldoria.updatebutler.config.Application;
import de.eldoria.updatebutler.config.Release;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;

import javax.annotation.Nullable;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ApplicationData extends QueryFactoryHolder {
    /**
     * Create a new QueryFactoryholder
     *
     * @param dataSource datasource
     * @param config     factory config
     */
    public ApplicationData(DataSource dataSource, QueryBuilderConfig config) {
        super(dataSource, config);
    }

    public CompletableFuture<Optional<Application>> applicationById(int id) {
        return builder(Application.class)
                .query("""
                        SELECT id,
                            guild_id,
                            identifier,
                            name,
                            channel,
                            webhook,
                            notify_role,
                            buyer_role,
                            alias,
                            owner
                        FROM application_view
                        WHERE id = ?
                        """)
                .paramsBuilder(p -> p.setInt(id))
                .readRow(r -> new Application(r.getInt("id"), r.getString("identifier"), r.getString("name"),
                        r.getString("description"), ArrayConverter.toSet(r, "alias"), ArrayConverter.toSet(r, "owner"),
                        r.getLong("channel"), r.getString("webhook")))
                .first();
    }

    public Optional<Integer> createApplication(Guild guild, String identifier, String name, String description) {
        var webhook = Hashing.sha256()
                .hashString(name + Instant.now().toEpochMilli(), StandardCharsets.UTF_8)
                .toString().toLowerCase();

        return builder(Integer.class)
                .query("INSERT INTO application(guild_id, identifier, name, webhook, description) VALUES(?,?,?,?,?) RETURNING id")
                .paramsBuilder(p -> p.setLong(guild.getIdLong()).setString(identifier).setString(name).setString(webhook).setString(description))
                .readRow(r -> r.getInt(1))
                .firstSync();
    }

    public CompletableFuture<Integer> addOwner(Application application, ISnowflake user) {
        return builder()
                .query("INSERT INTO application_owner(app_id, id) VALUES(?,?) ON CONFLICT DO NOTHING")
                .paramsBuilder(p -> p.setInt(application.getId()).setLong(user.getIdLong()))
                .insert()
                .execute();
    }

    public CompletableFuture<Integer> removeOwner(Application application, ISnowflake user) {
        return builder()
                .query("DELETE FROM application_owner WHERE app_id = ? AND id = ?")
                .paramsBuilder(p -> p.setInt(application.getId()).setLong(user.getIdLong()))
                .delete()
                .execute();
    }

    public CompletableFuture<Application> getReleases(Application application) {
        return builder(Release.class)
                .query("""
                        SELECT app_id,
                            version,
                            title,
                            patchnotes,
                            dev_build,
                            published,
                            downloads,
                            file,
                            checksum
                        FROM application_release
                        WHERE app_id = ?""")
                .paramsBuilder(p -> p.setInt(application.getId()))
                .readRow(r -> new Release(r.getString("version"), r.getString("title"), r.getString("patchnotes"),
                        r.getBoolean("dev_build"), r.getTimestamp("published").toLocalDateTime(), r.getString("file"),
                        r.getString("checksum"), r.getInt("downloads")))
                .all()
                .thenApply(releases -> {
                    application.setReleases(releases);
                    return application;
                });
    }

    public CompletableFuture<Optional<Release>> getRelease(Application application, String version) {
        return builder(Release.class)
                .query("""
                        SELECT app_id,
                            version,
                            title,
                            patchnotes,
                            dev_build,
                            published,
                            downloads,
                            file,
                            checksum
                        FROM application_release
                        WHERE app_id = ?
                            AND version = ?
                        """)
                .paramsBuilder(p -> p.setInt(application.getId()).setString(version))
                .readRow(r -> new Release(r.getString("version"), r.getString("title"), r.getString("patchnotes"),
                        r.getBoolean("dev_build"), r.getTimestamp("published").toLocalDateTime(), r.getString("file"),
                        r.getString("checksum"), r.getInt("downloads")))
                .first();
    }

    public void createRelease(Application application, String version, String title, String patchnotes, boolean devBuild,
                              @Nullable String file, @Nullable String checksum) {
        builder()
                .query("INSERT INTO application_release(app_id, version, title, patchnotes, dev_build, file, checksum) VALUES(?,?,?,?,?,?,?)")
                .paramsBuilder(p -> p.setInt(application.getId()).setString(version).setString(title)
                        .setString(patchnotes).setBoolean(devBuild).setString(file).setString(checksum))
                .insert()
                .execute();
    }

    public void deleteRelease(Application application, Release release) {
        builder().query("DELETE FROM application_release WHERE app_id = ? AND version = ?")
                .paramsBuilder(p -> p.setInt(application.getId()).setString(release.version()))
                .delete()
                .execute();
    }

    public void downloaded(Application application, Release release) {
        builder()
                .query("UPDATE application_release SET downloads = downloads + 1 WHERE app_id = ? AND version = ?")
                .paramsBuilder(p -> p.setInt(application.getId()).setString(release.version()));
    }
}

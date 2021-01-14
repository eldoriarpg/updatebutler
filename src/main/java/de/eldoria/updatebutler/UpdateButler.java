package de.eldoria.updatebutler;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.eldoria.updatebutler.api.WebAPI;
import de.eldoria.updatebutler.config.Configuration;
import de.eldoria.updatebutler.config.DBSettings;
import de.eldoria.updatebutler.listener.CommandListener;
import de.eldoria.updatebutler.listener.ReleaseCreateListener;
import de.eldoria.updatebutler.scheduler.TimeChannelScheduler;
import de.eldoria.updatebutler.util.ArgumentParser;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import javax.security.auth.login.LoginException;
import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public final class UpdateButler {
    private static UpdateButler instance;
    private static ReleaseCreateListener releaseCreateListener;
    private final Configuration configuration;
    private ShardManager shardManager = null;
    private WebAPI webAPI;
    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private DataSource source;

    private UpdateButler() throws IOException {
        configuration = Configuration.load();

        try {
            initiateJda();
        } catch (LoginException e) {
            log.error("jda failed to log in", e);
            return;
        }

        CommandListener commandListener = new CommandListener(configuration, shardManager);
        shardManager.addEventListener(commandListener);

        initializeDatabase();
        webAPI = new WebAPI(configuration, source);
        configuration.setReleaseListener(
                new ReleaseCreateListener(configuration, shardManager, new ArgumentParser(shardManager)));
        int min = 15 - (LocalDateTime.now().get(ChronoField.MINUTE_OF_HOUR) % 15) - 1;
        if (min < 1) {
            min = 14;
        }
        int sec = 60 - (LocalDateTime.now().get(ChronoField.SECOND_OF_MINUTE));
        log.info("Next time channel update in {} min {} sec", min, sec);
        executorService.scheduleAtFixedRate(new TimeChannelScheduler(shardManager, configuration), min * 60 + sec, 60 * 15, TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws LoginException, IOException {
        instance = new UpdateButler();
    }

    private void initiateJda() throws LoginException {
        shardManager = DefaultShardManagerBuilder
                .create(
                        configuration.getToken(),
                        GatewayIntent.DIRECT_MESSAGES,
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_EMOJIS)
                .setMaxReconnectDelay(60)
                .disableCache(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.VOICE_STATE)
                .enableCache(CacheFlag.MEMBER_OVERRIDES)
                .setBulkDeleteSplittingEnabled(false)
                .build();

        log.info("{} shards initialized", shardManager.getShardsTotal());
    }

    private void initializeDatabase() throws IllegalStateException {
        DBSettings settings = configuration.getDbSettings();
        Properties props = new Properties();
        props.setProperty("dataSourceClassName", "org.mariadb.jdbc.MariaDbDataSource");
        props.setProperty("dataSource.serverName", settings.getAddress());
        props.setProperty("dataSource.portNumber", settings.getPort());
        props.setProperty("dataSource.user", settings.getUser());
        props.setProperty("dataSource.password", settings.getPassword());
        props.setProperty("dataSource.databaseName", settings.getDatabase());

        HikariConfig config = new HikariConfig(props);

        config.setMaximumPoolSize(settings.getMaxConnections());

        source = new HikariDataSource(config);

        try (Connection conn = source.getConnection()) {
            boolean valid = conn.isValid(10000);
            if (!valid) {
                throw new IllegalStateException("Could not establish a valid database connection.");
            }
            log.info("Database connected.");
        } catch (SQLException e) {
            log.error("Connection is not valid.", e);
            throw new IllegalStateException("Could not establish a valid database connection.", e);
        }

        log.info("Ensuring database consistency.");

        String query;
        try (BufferedReader inputStream = new BufferedReader(
                new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream("setup.sql")))) {
            query = inputStream.lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            log.error("Could not load sql setup script", e);
            throw new IllegalStateException("Failed to ensure database consistency.", e);
        }
        for (String q : query.split(";")) {
            if (q.isBlank()) continue;
            try (Connection conn = source.getConnection(); PreparedStatement stmt = conn.prepareStatement(q)) {
                stmt.execute();
            } catch (SQLException e) {
                log.error("Could not update database", e);
                throw new IllegalStateException("Failed to ensure database consistency.", e);
            }
        }
        log.info("Database updated.");
    }
}

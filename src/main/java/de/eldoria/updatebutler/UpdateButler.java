package de.eldoria.updatebutler;

import de.eldoria.updatebutler.api.UpdatesAPI;
import de.eldoria.updatebutler.config.Configuration;
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
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class UpdateButler {
    private static UpdateButler instance;
    private static ReleaseCreateListener releaseCreateListener;
    private final Configuration configuration;
    private ShardManager shardManager = null;
    private UpdatesAPI updatesAPI;
    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

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


        updatesAPI = new UpdatesAPI(configuration);
        configuration.setReleaseListener(
                new ReleaseCreateListener(configuration, shardManager, new ArgumentParser(shardManager)));
        executorService.scheduleAtFixedRate(new TimeChannelScheduler(shardManager, configuration), 10, 1, TimeUnit.MINUTES);
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
                        GatewayIntent.GUILD_MESSAGES)
                .setMaxReconnectDelay(60)
                .disableCache(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS)
                .enableCache(CacheFlag.MEMBER_OVERRIDES)
                .setBulkDeleteSplittingEnabled(false)
                .build();

        log.info("{} shards initialized", shardManager.getShardsTotal());
    }
}

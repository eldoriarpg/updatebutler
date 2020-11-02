package de.eldoria.updatebutler.scheduler;

import de.eldoria.updatebutler.config.Configuration;
import de.eldoria.updatebutler.config.GuildSettings;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.sharding.ShardManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
public class TimeChannelScheduler implements Runnable {
    private final ShardManager manager;
    private final Configuration configuration;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("H:m");

    public TimeChannelScheduler(ShardManager manager, Configuration configuration) {
        this.manager = manager;
        this.configuration = configuration;
    }

    @Override
    public void run() {
        log.debug("Refreshing time channel.");
        for (Map.Entry<String, GuildSettings> entry : configuration.getGuildSettings().entrySet()) {
            long timeChannel = entry.getValue().getTimeChannel();
            if (timeChannel == 0) continue;
            GuildChannel channel = manager.getGuildChannelById(timeChannel);
            if (channel == null) continue;
            String s = "Developer Time: " + formatter.format(LocalDateTime.now().atZone(ZoneId.of(entry.getValue().getTimeZone())));
            log.debug("Refreshing time channel for guild {}. Setting to {}", entry.getKey(), s);
            channel.getManager().setName(s).submit();
        }
    }
}

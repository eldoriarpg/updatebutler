package de.eldoria.updatebutler.scheduler;

import de.eldoria.updatebutler.config.Configuration;
import de.eldoria.updatebutler.config.GuildSettings;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.sharding.ShardManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

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
        for (Map.Entry<String, GuildSettings> entry : configuration.getGuildSettings().entrySet()) {
            long timeChannel = entry.getValue().getTimeChannel();
            if (timeChannel == 0) continue;
            GuildChannel channel = manager.getGuildChannelById(timeChannel);
            if (channel == null) continue;
            channel.getManager().setName("Developer Time: " + formatter.format(LocalDateTime.now().atZone(ZoneId.of("GMT")))).submit();
        }
    }
}

package de.eldoria.updatebutler.listener;

import de.eldoria.updatebutler.config.Application;
import de.eldoria.updatebutler.config.Configuration;
import de.eldoria.updatebutler.config.Release;
import de.eldoria.updatebutler.util.ArgumentParser;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.sharding.ShardManager;

public class ReleaseCreateListener {
    private final Configuration configuration;
    private final ShardManager shardManager;
    private final ArgumentParser parser;

    public ReleaseCreateListener(Configuration configuration, ShardManager shardManager, ArgumentParser parser) {
        this.configuration = configuration;
        this.shardManager = shardManager;
        this.parser = parser;
    }

    public void onReleaseCreation(Application application, Release release) {
        if (application.getChannel() == null) return;
        TextChannel channel = shardManager.getTextChannelById(application.getChannel());
        if (channel == null) return;
        channel.sendMessage(application.getReleaseInfo(configuration, parser, channel.getGuild(), release)).queue();
    }
}

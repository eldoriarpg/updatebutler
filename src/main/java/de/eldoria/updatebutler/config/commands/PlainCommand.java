package de.eldoria.updatebutler.config.commands;

import com.google.gson.annotations.Expose;
import net.dv8tion.jda.api.entities.TextChannel;

public class PlainCommand extends UserCommand {
    @Expose
    private final String text;

    public PlainCommand(String command, String text) {
        super(command);
        this.text = text;
    }

    @Override
    public void sendCommandOutput(TextChannel channel) {
        channel.sendMessage(text).queue();
    }
}

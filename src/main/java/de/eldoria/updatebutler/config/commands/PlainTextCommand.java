package de.eldoria.updatebutler.config.commands;

import com.google.gson.annotations.Expose;
import net.dv8tion.jda.api.entities.TextChannel;

public class PlainTextCommand extends UserCommand {
    @Expose
    private final String text;

    public PlainTextCommand(String command, String text) {
        super(command);
        this.text = text;
    }

    @Override
    public void sendCommandOutput(TextChannel channel) {
        channel.sendMessage(text).queue();
    }
}

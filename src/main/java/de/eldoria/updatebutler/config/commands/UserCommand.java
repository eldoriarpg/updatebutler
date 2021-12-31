package de.eldoria.updatebutler.config.commands;

import com.google.common.base.Objects;
import net.dv8tion.jda.api.entities.TextChannel;

public abstract class UserCommand {
    private String command;

    public UserCommand() {
    }

    protected UserCommand(String command) {
        this.command = command;
    }

    /**
     * Sends the command output to a channel.
     *
     * @param channel channel to send
     */
    public abstract void sendCommandOutput(TextChannel channel);

    public boolean isCommand(String command) {
        return command.equalsIgnoreCase(this.command);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserCommand that = (UserCommand) o;
        return Objects.equal(command, that.command);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(command);
    }


    public String command() {
        return this.command;
    }
}

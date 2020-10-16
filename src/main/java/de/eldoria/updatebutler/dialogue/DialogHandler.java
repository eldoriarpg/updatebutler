package de.eldoria.updatebutler.dialogue;

import de.eldoria.updatebutler.config.Configuration;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;

import java.util.HashMap;
import java.util.Map;

public class DialogHandler {
    // Guild -> Channel -> User -> Dialogue
    private final Map<Long, Map<Long, Map<Long, Dialog>>> dialogs = new HashMap<>();
    private final Configuration configuration;

    public DialogHandler(Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * Invoke a dialog in this context.
     *
     * @param guild   guild of message
     * @param channel channel of message
     * @param member  member of message
     * @param message message of member
     *
     * @return true if a dialog was invoked.
     */
    public boolean invoke(Guild guild, TextChannel channel, Member member, Message message) {
        String content = message.getContentRaw();
        if ("exit".equalsIgnoreCase(content) || "cancel".equalsIgnoreCase(content)) {
            if (removeDialog(guild, channel, member)) {
                channel.sendMessage("Canceled.").queue();
            }
            return true;
        }

        Dialog dialog = getDialog(guild, channel, member);
        if (dialog != null) {
            if (dialog.invoke(guild, channel, member, message)) {
                removeDialog(guild, channel, member);
                configuration.save();
            }
            return true;
        }
        return false;
    }

    public boolean dialogInProgress(Guild guild, TextChannel channel, Member member) {
        return getDialog(guild, channel, member) != null;
    }

    public boolean removeDialog(Guild guild, TextChannel channel, Member member) {
        var guildDialogs = dialogs.get(guild.getIdLong());
        if (guildDialogs == null) return false;
        var channelDialogs = guildDialogs.get(channel.getIdLong());
        if (channelDialogs == null) return false;
        Dialog dialog = channelDialogs.get(member.getIdLong());
        if (dialog == null) return false;

        channelDialogs.remove(member.getIdLong());

        if (channelDialogs.isEmpty()) {
            guildDialogs.remove(channel.getIdLong());
        }

        if (guildDialogs.isEmpty()) {
            dialogs.remove(guild.getIdLong());
        }
        return true;
    }

    public void startDialog(Guild guild, TextChannel channel, Member member, String startMessage, Dialog dialog) {
        if (dialogInProgress(guild, channel, member)) {
            channel.sendMessage("A dialog is already in progress. Finish dialog or type \"exit\" to end the current dialog.").queue();
            return;
        }

        channel.sendMessage(startMessage).queue();

        dialogs.computeIfAbsent(guild.getIdLong(), k -> new HashMap<>())
                .computeIfAbsent(channel.getIdLong(), k -> new HashMap<>())
                .put(member.getIdLong(), dialog);
    }

    public Dialog getDialog(Guild guild, TextChannel channel, Member member) {
        var guildDialogs = dialogs.get(guild.getIdLong());
        if (guildDialogs == null) return null;
        var channelDialogs = guildDialogs.get(channel.getIdLong());
        if (channelDialogs == null) return null;
        return channelDialogs.get(member.getIdLong());
    }
}

package de.eldoria.updatebutler.dialogue;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;

public interface Dialog {
    boolean invoke(Guild guild, TextChannel channel, Member member, Message message);
}

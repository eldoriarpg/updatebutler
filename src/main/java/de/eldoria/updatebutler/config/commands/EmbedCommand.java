package de.eldoria.updatebutler.config.commands;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;

import java.util.ArrayList;
import java.util.List;

public class EmbedCommand extends UserCommand {
    private final String title;
    private final String descr;
    private final List<EmbedField> fields;

    @JsonIgnore
    private MessageEmbed embed;

    protected EmbedCommand(String command, String title, String descr, List<EmbedField> fields) {
        super(command);
        this.title = title;
        this.descr = descr;
        this.fields = fields;
    }

    @Override
    public void sendCommandOutput(TextChannel channel) {
        if (embed == null) {
            buildEmbed();
        }
        channel.sendMessageEmbeds(embed).queue();
    }

    private void buildEmbed() {
        EmbedBuilder builder = new EmbedBuilder().setTitle(title).setDescription(descr.isBlank() ? null : descr);
        for (EmbedField field : fields) {
            builder.addField(field.toField());
        }

        embed = builder.build();
    }

    public static class Builder {
        private final String command;
        @Getter
        private final List<EmbedField> fields = new ArrayList<>();
        @Getter
        private String title = null;
        @Getter
        private String descr = null;

        public Builder(String command) {
            this.command = command;
        }

        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder setDescr(String descr) {
            this.descr = descr;
            return this;
        }

        public Builder addField(EmbedField field) {
            fields.add(field);
            return this;
        }

        public EmbedCommand build() {
            return new EmbedCommand(command, title, descr, fields);
        }
    }

    @AllArgsConstructor
    public static class EmbedField {
        private final String title;
        private final String text;
        private final boolean inline;

        public MessageEmbed.Field toField() {
            return new MessageEmbed.Field(title, text, inline);
        }
    }
}

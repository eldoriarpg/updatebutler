package de.eldoria.updatebutler.listener;

import de.eldoria.updatebutler.config.Application;
import de.eldoria.updatebutler.config.Configuration;
import de.eldoria.updatebutler.config.GuildSettings;
import de.eldoria.updatebutler.config.Release;
import de.eldoria.updatebutler.config.ReleaseBuilder;
import de.eldoria.updatebutler.config.commands.CommandType;
import de.eldoria.updatebutler.config.commands.EmbedCommand;
import de.eldoria.updatebutler.config.commands.PlainCommand;
import de.eldoria.updatebutler.config.commands.UserCommand;
import de.eldoria.updatebutler.config.phrase.Phrase;
import de.eldoria.updatebutler.config.phrase.PhraseType;
import de.eldoria.updatebutler.config.phrase.PlainPhrase;
import de.eldoria.updatebutler.config.phrase.RegexPhrase;
import de.eldoria.updatebutler.dialogue.Dialog;
import de.eldoria.updatebutler.dialogue.DialogHandler;
import de.eldoria.updatebutler.util.ArgumentParser;
import de.eldoria.updatebutler.util.C;
import de.eldoria.updatebutler.util.TextFormatting;
import de.eldoria.updatebutler.util.Verifier;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.message.guild.GenericGuildMessageEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.time.zone.ZoneRulesProvider;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class CommandListener extends ListenerAdapter {
    private static final Pattern VERSION = Pattern.compile("^[\\sa-zA-Z0-9.\\-_]+$");
    private static final Pattern ID_PATTERN = Pattern.compile("(?:<[@#!&]{1,2})?(?<id>[0-9]{18})(?:>)?");
    private final Configuration configuration;
    private final ArgumentParser parser;
    private final String[] userCommands = {"latestVersion", "versions", "versionInfo", "info", "applist"};
    private final String[] ownerCommands = {"setPrefix", "grant", "revoke", "createApp", "createCommand", "removeCommand", "createPhrase", "removePhrase", "listPhrase", "setTimeChannel"};
    private final String[] appCommands = {"deleteApp", "grantAccess", "revokeAccess", "deployUpdate", "deleteUpdate", "setName", "setDescr", "setAlias", "setChannel"};
    private final DialogHandler dialogHandler;

    public CommandListener(Configuration configuration, ShardManager shardManager) {
        this.configuration = configuration;
        this.parser = new ArgumentParser(shardManager);
        dialogHandler = new DialogHandler(configuration);
    }

    public static boolean isInArray(String s, String... sA) {
        for (String s1 : sA) {
            if (s1.equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    @Override
    public void onGuildMessageUpdate(@Nonnull GuildMessageUpdateEvent event) {
        handleMessage(event.getMember(), event.getChannel(), event.getGuild(), event.getMessage(), event);
    }

    @Override
    public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event) {
        handleMessage(event.getMember(), event.getChannel(), event.getGuild(), event.getMessage(), event);
    }

    public void handleMessage(Member member, TextChannel channel, Guild guild, Message message, GenericGuildMessageEvent event) {
        if (member.getUser().isBot()) return;

        GuildSettings guildSettings = configuration.getGuildSettings(guild.getId());

        String receivedMessage = message.getContentRaw();
        receivedMessage = receivedMessage.replaceAll("\\s\\s+", " ");
        String[] args = receivedMessage.split(" ");

        if (!isCommand(receivedMessage, args, guildSettings, event)) {
            if (dialogHandler.invoke(guild, channel, member, message)) {
                return;
            }
            guildSettings.matchPhrase(receivedMessage)
                    .flatMap(p -> guildSettings.getUserCommand(p.getCommand()))
                    .ifPresent(c -> c.sendCommandOutput(channel));
            return;
        }

        args = stripArgs(receivedMessage, args, guildSettings, event);
        String label = args[0];
        args = buildArgs(args);

        if ("help".equalsIgnoreCase(label)) {
            help(member, channel, guildSettings);
            return;
        }

        // public commands
        if (isInArray(label, userCommands)) {
            if ("appList".equalsIgnoreCase(label)) {
                applist(channel, guildSettings);
                return;
            }

            if (args.length < 1) {
                channel.sendMessage("Please provide a application identifier or alias").queue();
                return;
            }

            Optional<Application> optApplication = guildSettings.getApplication(args[0]);
            if (optApplication.isEmpty()) {
                channel.sendMessage("Invalid application name. Please use a identifier or alias").queue();
                return;
            }

            Application application = optApplication.get();

            if ("latestVersion".equalsIgnoreCase(label)) {
                latestVersion(channel, event, args, application);
            }

            if ("versions".equalsIgnoreCase(label)) {
                versions(channel, args, application);
            }
            if ("versionInfo".equalsIgnoreCase(label)) {
                versionInfo(channel, event, args, application);
            }

            if ("info".equalsIgnoreCase(label)) {
                channel.sendMessage(application.getApplicationInfo(configuration, event.getGuild(), parser)).queue();
            }
            return;
        }

        // Owner Commands
        if (isInArray(label, ownerCommands)) {
            if (!guildSettings.isAllowedUser(member)) {
                channel.sendMessage("You are not a bot owner!").queue();
                return;
            }

            if ("setPrefix".equalsIgnoreCase(label)) {
                setPrefix(channel, guildSettings, args);
            }

            if ("grant".equalsIgnoreCase(label)) {
                grant(member, channel, guild, guildSettings, args);
            }

            if ("revoke".equalsIgnoreCase(label)) {
                revoke(member, channel, guild, guildSettings, args);
            }

            if ("createApp".equalsIgnoreCase(label)) {
                createApp(member, channel, guild, guildSettings);
            }
            if ("createCommand".equalsIgnoreCase(label)) {
                createCommand(member, channel, guild, guildSettings);
            }
            if ("removeCommand".equalsIgnoreCase(label)) {
                removeCommand(member, channel, guild, guildSettings);
            }
            if ("createPhrase".equalsIgnoreCase(label)) {
                createPhrase(member, channel, guild, guildSettings);
            }
            if ("removePhrase".equalsIgnoreCase(label)) {
                removePhrase(member, channel, guild, guildSettings);
            }
            if ("listPhrase".equalsIgnoreCase(label)) {
                listPhrase(member, channel, guild, guildSettings);
            }
            if ("setTimeChannel".equalsIgnoreCase(label)) {
                setTimeChannel(member, channel, guild, guildSettings);
            }
            return;
        }

        // Application Commands
        if (isInArray(label, appCommands)) {
            if (!guildSettings.isAllowedUser(member) && !member.isOwner()) {
                channel.sendMessage("You are not a bot owner!").queue();
                return;
            }

            if ("deployUpdate".equalsIgnoreCase(label)) {
                deployUpdate(member, channel, guild, guildSettings);
                return;
            }
            if ("deleteApp".equalsIgnoreCase(label)) {
                deleteApp(member, channel, guild, guildSettings);
            }

            if ("grantAccess".equalsIgnoreCase(label)) {
                grantAccess(member, channel, guild, guildSettings);
            }

            if ("revokeAccess".equalsIgnoreCase(label)) {
                revokeAccess(member, channel, guild, guildSettings);
            }

            if ("deleteUpdate".equalsIgnoreCase(label)) {
                deleteUpdate(member, channel, guild, guildSettings);
            }

            if (args.length == 0) {
                channel.sendMessage("Please enter a application name or alias").queue();
                return;
            }

            Optional<Application> optApp = guildSettings.getApplication(args[0]);

            if (optApp.isEmpty()) {
                channel.sendMessage("This application does not exist. Use the application identifier or alias").queue();
                return;
            }

            Application application = optApp.get();

            if (!application.isOwner(member)) {
                channel.sendMessage("You are not an owner of this application.").queue();
                return;
            }


            if ("setName".equalsIgnoreCase(label)) {
                setName(channel, args, application);
            }

            if ("setDescr".equalsIgnoreCase(label)) {
                setDescription(channel, args, application);
            }

            if ("setAlias".equalsIgnoreCase(label)) {
                setAlias(channel, args, application);
            }

            if ("setChannel".equalsIgnoreCase(label)) {
                setChannel(channel, event, args, application);
            }
            return;
        }
        Optional<Application> optional = guildSettings.getApplication(label);

        if (optional.isPresent()) {
            channel.sendMessage(optional.get().getApplicationInfo(configuration, event.getGuild(), parser)).queue();
            return;
        }

        Optional<UserCommand> userCommand = guildSettings.getUserCommand(label);

        if (userCommand.isPresent()) {
            userCommand.get().sendCommandOutput(channel);
        }
        //channel.sendMessage("Invalid command.").queue();
    }

    private void setTimeChannel(Member member, TextChannel channel, Guild guild, GuildSettings guildSettings) {
        dialogHandler.startDialog(guild, channel, member, "Please mention a channel to set the time channel or \"none\" to remove the current channel.", new Dialog() {
            private Long id = null;

            @Override
            public boolean invoke(Guild guild, TextChannel channel, Member member, Message message) {
                String content = message.getContentRaw();
                if (id == null) {
                    if ("none".equalsIgnoreCase(content)) {
                        guildSettings.setTimeChannel(0);
                        channel.sendMessage("Time channel removed.").queue();
                        return true;
                    }

                    Optional<GuildChannel> textChannel = parser.getGuildChannel(guild, content);

                    if (textChannel.isEmpty()) {
                        channel.sendMessage("Invalid channel." +
                                "\nPlease mention a channel to set the time channel or \"none\" to remove the current channel.").queue();
                        return false;
                    }
                    id = textChannel.get().getIdLong();
                    channel.sendMessage("Set time channel to **" + textChannel.get().getName() + "**.\n" +
                            "In which timezone do you live?").queue();
                    return false;
                }

                Set<String> availableZoneIds = ZoneRulesProvider.getAvailableZoneIds();

                String zone = null;

                for (String availableZoneId : availableZoneIds) {
                    if (availableZoneId.equalsIgnoreCase(content)) {
                        zone = availableZoneId;
                    }
                }

                if (zone == null) {
                    channel.sendMessage("Invalid time zone\nhttp://tutorials.jenkov.com/java-date-time/java-util-timezone.html#available-time-zones").queue();
                    return false;
                }

                guildSettings.setTimeZone(zone);
                guildSettings.setTimeChannel(id);
                channel.sendMessage("Timezone set. Channel will be updated automatically. In the next 5 minutes").queue();
                return true;
            }
        });
    }



    /*
    PUBLIC COMMANDS START
     */

    private void help(Member member, TextChannel channel, GuildSettings guildSettings) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("Help");
        String userCommands = Arrays.stream(this.userCommands).map(s -> StringUtils.wrap(s, '`')).collect(Collectors.joining(", "));
        String ownerCommands = Arrays.stream(this.ownerCommands).map(s -> StringUtils.wrap(s, '`')).collect(Collectors.joining(", "));
        String appCommands = Arrays.stream(this.appCommands).map(s -> StringUtils.wrap(s, '`')).collect(Collectors.joining(", "));
        builder.addField("User Commands:", userCommands, false);

        if (guildSettings.isAllowedUser(member) || member.isOwner()) {
            builder.addField("Owner Commands:", ownerCommands, false);
        }

        if (guildSettings.hasApplication(member)) {
            builder.addField("Application Commands:", appCommands, false);
        }

        String customCommands = guildSettings.getUserCommands();
        String customAppCommands = guildSettings.getApplicationCommands();

        if (!customCommands.isEmpty()) {
            builder.addField("Custom Commands:", customCommands, false);
        }

        if (!customAppCommands.isEmpty()) {
            builder.addField("Applications:", customAppCommands, false);
        }

        channel.sendMessage(builder.build()).queue();
    }

    private void applist(TextChannel channel, GuildSettings guildSettings) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Available Applications");
        guildSettings.getApplications().values().stream()
                .sorted(Comparator.comparing(Application::getId))
                .forEach(o -> embedBuilder.addField("#" + o.getId() + " " + o.getDisplayName(),
                        "Identifier: " + o.getIdentifier() + "\n"
                                + "Alias: " + String.join(", ", o.getAlias()),
                        true));
        channel.sendMessage(embedBuilder.build()).queue();
    }

    private void latestVersion(TextChannel channel, GenericGuildMessageEvent event, String[] args, Application application) {
        boolean dev = true;
        if (args.length > 1) {
            Optional<Boolean> aBoolean = ArgumentParser.parseBoolean(args[1], "dev", "stable");
            if (aBoolean.isEmpty()) {
                channel.sendMessage("latestVersion <application> <dev|stable>").queue();
                return;
            }
            dev = aBoolean.get();
        }
        Optional<Release> release;
        if (dev) {
            release = application.getLatestVersion();
        } else {
            release = application.getLatestStableVersion();
            if (release.isEmpty()) {
                release = application.getLatestVersion();
            }
        }
        if (release.isEmpty()) {
            channel.sendMessage("No releases published for this application").queue();
            return;
        }

        MessageEmbed releaseInfo = application.getReleaseInfo(configuration, parser, event.getGuild(), release.get());
        channel.sendMessage(releaseInfo).queue();
    }

    private void versions(TextChannel channel, String[] args, Application application) {
        boolean dev = true;
        if (args.length > 1) {
            Optional<Boolean> aBoolean = ArgumentParser.parseBoolean(args[1], "dev", "stable");
            if (aBoolean.isEmpty()) {
                channel.sendMessage("latestVersion <application> <dev|stable>").queue();
                return;
            }
            dev = aBoolean.get();
        }

        List<Release> releases = application.getReleases(dev);
        TextFormatting.TableBuilder tableBuilder = TextFormatting.getTableBuilder(Math.min(releases.size(), 10), "Build", "Version", "Published", "Downloads");

        for (int i = 0; i < releases.size() && i < 10; i++) {
            Release release = releases.get(i);
            tableBuilder.setNextRow(
                    String.valueOf(release.isDevBuild()),
                    release.version(),
                    C.DATE_FORMAT.format(release.published()),
                    String.valueOf(release.downloads()));
        }

        channel.sendMessage("Versions of " + application.getDisplayName() + "\n"
                + tableBuilder.toString()
                + "and " + Math.max(releases.size() - 10, 0) + " more.").queue();
    }

    private void versionInfo(TextChannel channel, GenericGuildMessageEvent event, String[] args, Application application) {
        if (args.length < 2) {
            channel.sendMessage("versionInfo <application> <version>").queue();
            return;
        }
        Optional<Release> release = application.getRelease(args[1]);

        if (release.isEmpty()) {
            channel.sendMessage("This release does not exist.").queue();
            return;
        }

        MessageEmbed releaseInfo = application.getReleaseInfo(configuration, parser, event.getGuild(), release.get());
        channel.sendMessage(releaseInfo).queue();
    }


    /*
    PUBLIC COMMANDS END
     */
    /*
    OWNER COMMANDS START
     */
    private void createCommand(Member member, TextChannel channel, Guild guild, GuildSettings guildSettings) {
        dialogHandler.startDialog(guild, channel, member, "Please enter the command name.", new Dialog() {
            private String command = null;
            private UserCommand userCommand = null;
            private CommandType commandType = null;

            private EmbedCommand.Builder builder;
            private boolean fieldCreation = false;
            private String title;
            private String text;

            @Override
            public boolean invoke(Guild guild, TextChannel channel, Member member, Message message) {
                String content = message.getContentRaw();
                if (command == null && userCommand == null) {
                    if (content.contains(" ")) {
                        channel.sendMessage("Commands can't have spaces.").queue();
                        return false;
                    }

                    Optional<UserCommand> userCommand = guildSettings.getUserCommand(content);
                    if (userCommand.isPresent()) {
                        channel.sendMessage("This command already exist and will be edited.\nPlease choose the command type: **plain** or **embed**").queue();
                        this.userCommand = userCommand.get();
                    } else {
                        command = content;
                        channel.sendMessage("Command set to: **" + content + "**.\nPlease choose the command type: **plain** or **embed**").queue();
                    }
                    return false;
                }

                if (commandType == null) {
                    CommandType enumIgnoreCase = EnumUtils.getEnumIgnoreCase(CommandType.class, content);
                    if (enumIgnoreCase == null) {
                        channel.sendMessage("Invalid input.\n.Please choose the command type: **plain** or **embed**").queue();
                        return false;
                    }
                    commandType = enumIgnoreCase;
                    if (commandType == CommandType.PLAIN) {
                        channel.sendMessage("Creating new plain text command.\nPlease enter the text which should be send.").queue();
                    } else {
                        channel.sendMessage("Creating new embed text command.\nPlease enter the embed title.").queue();
                        builder = new EmbedCommand.Builder(this.command == null ? userCommand.command() : this.command);
                    }
                    return false;
                }

                if (commandType == CommandType.PLAIN) {
                    PlainCommand command = new PlainCommand(this.command == null ? userCommand.command() : this.command, content);
                    guildSettings.addUserCommand(command);
                    channel.sendMessage("Command **" + command.command() + "** created.\nOutput:").queue();
                    command.sendCommandOutput(channel);
                    return true;
                } else {
                    if (builder.getTitle() == null) {
                        builder.setTitle(content);
                        channel.sendMessage("Title set to: **" + content + "**.\nPlease enter a description or \"none\".").queue();
                        return false;
                    }
                    if (builder.getDescr() == null) {
                        if (content.equalsIgnoreCase("none")) {
                            builder.setDescr("");
                            channel.sendMessage("No description set.").queue();
                        } else {
                            builder.setDescr(content);
                            channel.sendMessage("Description set to:\n" + content).queue();
                        }
                        channel.sendMessage("Do you want to add a field? **yes/no**").queue();
                        return false;
                    }
                    if (!fieldCreation) {
                        if ("yes".equalsIgnoreCase(content)) {
                            fieldCreation = true;
                            channel.sendMessage("Please enter a field title.").queue();
                            return false;
                        }
                        if ("no".equalsIgnoreCase(content)) {
                            EmbedCommand command = builder.build();
                            guildSettings.addUserCommand(command);
                            channel.sendMessage("Command **" + command.command() + "** created.\nOutput:").queue();
                            command.sendCommandOutput(channel);
                            return true;
                        }
                        channel.sendMessage("Do you want to add a field? **yes/no**").queue();
                    } else {
                        if (title == null) {
                            title = content;
                            channel.sendMessage("Field title set to: **" + content + "**.\nPlease enter the field text.").queue();
                            return false;
                        }
                        if (text == null) {
                            text = content;
                            channel.sendMessage("Text set to:\n" + content + "\nShould this field be inline? **yes/no**").queue();
                            return false;
                        }
                        Optional<Boolean> aBoolean = ArgumentParser.parseBoolean(content, "yes", "no");
                        if (aBoolean.isPresent()) {
                            boolean inline = aBoolean.get();
                            if (inline) {
                                channel.sendMessage("New inline field added.").queue();
                            } else {
                                channel.sendMessage("New field added.").queue();
                            }
                            builder.addField(new EmbedCommand.EmbedField(title, text, inline));
                            text = null;
                            title = null;
                            fieldCreation = false;
                            channel.sendMessage("Do you want to add another field? **yes/no**").queue();
                        } else {
                            channel.sendMessage("Invalid Input\nShould this field be inline? **yes/no**").queue();
                        }
                    }
                    return false;
                }
            }
        });
    }

    private void removeCommand(Member member, TextChannel channel, Guild guild, GuildSettings guildSettings) {
        String userCommands = guildSettings.getUserCommands();
        dialogHandler.startDialog(guild, channel, member, "Which command do you want to remove?\n" + userCommands, new Dialog() {
            private final String command = null;
            private final UserCommand userCommand = null;

            @Override
            public boolean invoke(Guild guild, TextChannel channel, Member member, Message message) {
                String content = message.getContentRaw();
                Optional<UserCommand> userCommand = guildSettings.getUserCommand(content);

                if (userCommand.isEmpty()) {
                    channel.sendMessage("Invalid command.\nWhich command do you want to remove?\n" + userCommands).queue();
                    return false;
                }
                guildSettings.removeCommand(userCommand.get());
                channel.sendMessage("Command **" + userCommand.get().command() + "** removed.").queue();
                return true;
            }
        });
    }

    private void createPhrase(Member member, TextChannel channel, Guild guild, GuildSettings guildSettings) {
        dialogHandler.startDialog(guild, channel, member, "Choose match type: **regex/plain**.",
                new Dialog() {
                    private final String command = null;
                    private PhraseType phraseType = null;
                    private String phrase = null;
                    private Boolean caseSensitive = null;

                    @Override
                    public boolean invoke(Guild guild, TextChannel channel, Member member, Message message) {
                        String content = message.getContentRaw();
                        if (phraseType == null) {
                            phraseType = EnumUtils.getEnumIgnoreCase(PhraseType.class, content);
                            if (phraseType == null) {
                                channel.sendMessage("Invalid match type.\nChoose match type: **regex/plain**.").queue();
                                return false;
                            }
                            channel.sendMessage("Phrase type set to: **" + phraseType + "**").queue();
                            if (phraseType == PhraseType.PLAIN) {
                                channel.sendMessage("Should the phrase be case sentitive? **yes|no**").queue();
                            } else if (phraseType == PhraseType.REGEX) {
                                channel.sendMessage("Please enter a regex pattern.").queue();
                            }
                            return false;
                        }

                        switch (phraseType) {
                            case PLAIN:
                                if (caseSensitive == null) {
                                    Optional<Boolean> aBoolean = ArgumentParser.parseBoolean(content, "yes", "no");
                                    if (aBoolean.isEmpty()) {
                                        channel.sendMessage("Invalid Input.\n"
                                                + "Should the phrase be case sentitive? **yes|no**").queue();
                                        return false;
                                    }
                                    caseSensitive = aBoolean.get();
                                    if (caseSensitive) {
                                        channel.sendMessage("Matching will be case sensitive").queue();
                                    } else {
                                        channel.sendMessage("Matching will **NOT** be case sensitive").queue();
                                    }
                                    channel.sendMessage("Please enter a phrase to match.").queue();
                                    return false;
                                }
                                if (phrase == null) {
                                    phrase = content;
                                    String applicationCommands = guildSettings.getUserCommands();
                                    channel.sendMessage("Phrase set to: ```" + phrase
                                            + "``` Please enter a command which should be used as response:\n"
                                            + applicationCommands).queue();
                                    return false;
                                }
                                break;
                            case REGEX:
                                if (phrase == null) {
                                    phrase = content;
                                    String applicationCommands = guildSettings.getUserCommands();
                                    channel.sendMessage("Regex set to: ```" + phrase
                                            + "``` Please enter a command which should be used as response:\n"
                                            + applicationCommands).queue();
                                    return false;
                                }
                                break;
                        }

                        if (command == null) {
                            Optional<UserCommand> userCommand = guildSettings.getUserCommand(content);
                            if (userCommand.isEmpty()) {
                                String applicationCommands = guildSettings.getUserCommands();
                                channel.sendMessage("Invalid command\n"
                                        + "Please enter a command which should be used as response:\n"
                                        + applicationCommands).queue();
                                return false;
                            }
                            switch (phraseType) {
                                case REGEX:
                                    guildSettings.addPhrase(new RegexPhrase(phrase, userCommand.get().command()));
                                    channel.sendMessage("New regex phrase created.").queue();
                                    break;
                                case PLAIN:
                                    guildSettings.addPhrase(new PlainPhrase(phrase, userCommand.get().command(), caseSensitive));
                                    channel.sendMessage("New plain phrase created.").queue();
                                    break;
                            }
                            return true;
                        }
                        return false;
                    }
                });
    }

    private void removePhrase(Member member, TextChannel channel, Guild guild, GuildSettings guildSettings) {
        dialogHandler.startDialog(guild, channel, member,
                "Which phrase do you want to remove? Please write the number.\n" + guildSettings.getPhrases(),
                (g, ch, mem, mes) -> {
                    String content = mes.getContentRaw();
                    OptionalInt optionalInt = ArgumentParser.parseInt(content);
                    if (optionalInt.isEmpty()) {
                        ch.sendMessage("This is not a number.\nPlease use the number of the phrase.").queue();
                        return false;
                    }
                    Optional<Phrase> phrase = guildSettings.removePhrase(optionalInt.getAsInt() - 1);
                    if (phrase.isEmpty()) {
                        ch.sendMessage("Invalid phrase number.").queue();
                        return false;
                    }
                    ch.sendMessage("Phrase ```" + phrase.get().getPhrase() + "``` removed.").queue();
                    return true;
                });
    }

    private void listPhrase(Member member, TextChannel channel, Guild guild, GuildSettings guildSettings) {
        channel.sendMessage("Registered Phrases:\n" + guildSettings.getPhrases()).queue();
    }

    private void setPrefix(TextChannel channel, GuildSettings guildSettings, String[] args) {
        if (args.length != 1) {
            channel.sendMessage("setPrefix <prefix>").queue();
            return;
        }

        guildSettings.setPrefix(args[0]);
        channel.sendMessage("Prefix set to **" + args[0] + "**").queue();
        configuration.save();
    }

    private void grant(Member member, TextChannel channel, Guild guild, GuildSettings guildSettings, String[] args) {
        if (args.length != 1) {
            channel.sendMessage("grant <users...>").queue();
            return;
        }

        List<Member> guildMembers = parser.getGuildMembers(guild, Arrays.asList(args));

        for (Member guildMember : guildMembers) {
            guildSettings.addAllowedUser(guildMember);
        }

        String names = guildMembers.stream().map(m -> "**" + m.getEffectiveName() + "**").collect(Collectors.joining(", "));

        channel.sendMessage("Granted bot usage to: " + names).queue();
        configuration.save();
    }

    private void revoke(Member member, TextChannel channel, Guild guild, GuildSettings guildSettings, String[] args) {
        if (args.length == 0) {
            channel.sendMessage("revoke <users...>").queue();
            return;
        }

        List<Member> guildMembers = parser.getGuildMembers(guild, Arrays.asList(args));

        for (Member guildMember : guildMembers) {
            if (Verifier.equalSnowflake(guildMember, member)) continue;
            guildSettings.removeAllowedUser(guildMember);
        }

        String names = guildMembers.stream().map(m -> "**" + m.getEffectiveName() + "**").collect(Collectors.joining(", "));

        channel.sendMessage("Revoked bot usage from: " + names).queue();
        configuration.save();
    }

    private void createApp(Member member, TextChannel channel, Guild guild, GuildSettings guildSettings) {

        dialogHandler.startDialog(guild, channel, member, "Please enter the application identifier.",
                new Dialog() {
                    private String id = null;
                    private String displayName = null;
                    private String[] alias = null;
                    private String description = null;

                    @Override
                    public boolean invoke(Guild guild, TextChannel channel, Member member, Message message) {
                        String content = message.getContentRaw();
                        if (id == null) {
                            String applicationId = content.replace(" ", "_");
                            Optional<Application> application = guildSettings.getApplication(applicationId);
                            if (application.isPresent()) {
                                channel.sendMessage("This id identifier is already in use.").queue();
                                channel.sendMessage("Please enter the application identifier.").queue();
                                return false;
                            }
                            id = applicationId;
                            channel.sendMessage("Application identifier set to: **" + id + "**.").queue();
                            channel.sendMessage("Please enter the application name.").queue();
                            return false;
                        }

                        if (displayName == null) {
                            displayName = content;
                            channel.sendMessage("Application name set to: **" + displayName + "**.").queue();
                            channel.sendMessage("Enter one or more short aliases for the application or none for no alias.").queue();
                            return false;
                        }

                        if (alias == null) {
                            if (!"none".equalsIgnoreCase(content)) {
                                alias = content.split("\\s");
                                channel.sendMessage("Aliases defined: **" + String.join(", ", alias) + "**.").queue();
                            } else {
                                alias = new String[0];
                                channel.sendMessage("No aliases set.").queue();
                            }
                            channel.sendMessage("Please provide a short description of the application.").queue();
                            return false;
                        }

                        if (description == null) {
                            description = content;
                            channel.sendMessage("Description set to:\n>>> " + description).queue();
                            channel.sendMessage("Please enter a channel where updates should be posted or none.").queue();
                            return false;
                        }

                        Long updateChannel = null;
                        if (!"none".equalsIgnoreCase(content)) {
                            var textChannel = ArgumentParser.getTextChannel(guild, content);
                            if (textChannel.isEmpty()) {
                                channel.sendMessage("This channel is invalid").queue();
                                channel.sendMessage("Please enter a channel where updates should be posted or none.").queue();
                                return false;
                            }
                            updateChannel = textChannel.get().getIdLong();
                            channel.sendMessage("Update channel set to " + textChannel.get().getAsMention() + ".").queue();
                        } else {
                            channel.sendMessage("No update channel set.").queue();
                        }
                        int nextAppId = configuration.getNextAppId();
                        guildSettings.addApplication(Integer.toString(nextAppId),
                                new Application(nextAppId, id, displayName, description, alias, member.getIdLong(), updateChannel));
                        channel.sendMessage("Application registered with id " + nextAppId).queue();
                        configuration.save();
                        return true;
                    }
                });
    }


    /*
    OWNER COMMANDS END
     */

    /*
    APPLICATION COMMANDS START
     */

    private void deployUpdate(Member member, TextChannel channel, Guild guild, GuildSettings guildSettings) {
        String applicationNames = getUserApplicationNames(guildSettings, member);

        dialogHandler.startDialog(guild, channel, member,
                "For which application do you want to deploy an update?\n" + applicationNames,
                new Dialog() {
                    private Application application;
                    private String version;
                    private String title;
                    private String description;
                    private Boolean devBuild;

                    @Override
                    public boolean invoke(Guild guild, TextChannel channel, Member member, Message message) {
                        if (application == null) {
                            Optional<Application> application = guildSettings.getApplication(message.getContentRaw());
                            if (application.isEmpty()) {
                                channel.sendMessage("This application does not exist.\n" +
                                        "For which application do you want to deploy an update?\n" + applicationNames).queue();
                                return false;
                            }

                            if (!application.get().isOwner(member)) {
                                channel.sendMessage("This is not your application.\n" +
                                        "For which application do you want to deploy an update?\n" + applicationNames).queue();
                                return false;
                            }

                            this.application = application.get();
                            channel.sendMessage("Selected application *" + this.application.getDisplayName() + "*.").queue();
                            channel.sendMessage("Please enter the version string (E.g. 1.0.1)").queue();
                            return false;
                        }

                        String content = message.getContentRaw();
                        if (version == null) {
                            if (!VERSION.matcher(content).find()) {
                                channel.sendMessage("Only following characters are allowed: `a-z A-Z 0-9 . - _` and space.").queue();
                                return false;
                            }
                            version = content;
                            channel.sendMessage("Version set to: *" + version + "*.").queue();
                            channel.sendMessage("Please enter a update title.").queue();
                            return false;
                        }

                        if (title == null) {
                            title = content;
                            channel.sendMessage("Title set to: *" + title + "*.").queue();
                            channel.sendMessage("Please enter the patchnotes.").queue();
                            return false;
                        }

                        if (description == null) {
                            if (content.length() > 1024) {
                                channel.sendMessage("Patchnotes are too long. " + content.length() + "/1024").queue();
                                return false;
                            }
                            description = content;
                            channel.sendMessage("Patchnotes set to:\n" + description).queue();
                            channel.sendMessage("Is this update a dev build? [yes|no]").queue();
                            return false;
                        }

                        if (devBuild == null) {
                            Optional<Boolean> dev = ArgumentParser.parseBoolean(content, "yes", "no");
                            if (dev.isEmpty()) {
                                channel.sendMessage("Is this update a dev build? [yes|no]").queue();
                                return false;
                            }
                            devBuild = dev.get();
                            if (devBuild) {
                                channel.sendMessage("This version is a dev build.").queue();
                            } else {
                                channel.sendMessage("This version is a stable build").queue();
                            }
                            channel.sendMessage("Please upload an update file.").queue();
                            return false;
                        }

                        if (message.getAttachments().isEmpty()) {
                            channel.sendMessage("This message does not contain an update file.").queue();
                            return false;
                        }

                        Message.Attachment attachment = message.getAttachments().get(0);

                        Optional<Release> buildRelease = ReleaseBuilder.buildRelease(application, version, title, description, message.getAttachments().get(0).getUrl(), devBuild);

                        if (buildRelease.isEmpty()) {
                            channel.sendMessage("An error occured while creating the release.").queue();
                            channel.sendMessage("Please upload an update file.").queue();
                            return false;
                        }

                        channel.sendMessage("Created new release!").queue();
                        channel.sendMessage(application.getReleaseInfo(configuration, parser, guild, buildRelease.get())).queue();

                        configuration.addRelease(application, buildRelease.get());
                        return true;
                    }
                });
    }

    private void deleteApp(Member member, TextChannel channel, Guild guild, GuildSettings guildSettings) {
        String userApplicationNames = getUserApplicationNames(guildSettings, member);

        dialogHandler.startDialog(guild, channel, member,
                "Which application do you want to delete?\n" + userApplicationNames,
                new Dialog() {
                    private Application application;

                    @Override
                    public boolean invoke(Guild guild, TextChannel channel, Member member, Message message) {
                        String content = message.getContentRaw();
                        if (application == null) {
                            Optional<Application> application = guildSettings.getApplication(content);
                            if (application.isPresent()) {
                                this.application = application.get();
                                channel.sendMessage("Application **" + this.application.getDisplayName() + "** selected.\n"
                                        + "Please confirm by typing \"confirm\"").queue();
                                return false;
                            }
                            channel.sendMessage("Invalid application.\n"
                                    + "Which application do you want to delete?\n" + userApplicationNames).queue();
                            return false;
                        }

                        if ("confirm".equalsIgnoreCase(content)) {
                            guildSettings.removeApplication(application.getIdentifier());
                            channel.sendMessage("Application **" + this.application.getDisplayName() + "** selected.\n"
                                    + "Please confirm by typing \"confirm\"").queue();
                            return true;
                        }
                        channel.sendMessage("Application **" + this.application.getDisplayName() + "** selected.\n"
                                + "Please confirm by typing \"confirm\"").queue();
                        configuration.save();
                        return false;
                    }
                });
    }

    private void grantAccess(Member member, TextChannel channel, Guild guild, GuildSettings guildSettings) {

        String userApplicationNames = getUserApplicationNames(guildSettings, member);

        dialogHandler.startDialog(guild, channel, member,
                "Please select a application.\n" + userApplicationNames,
                new Dialog() {
                    private final Set<User> users = new HashSet<>();
                    private Application application;

                    @Override
                    public boolean invoke(Guild guild, TextChannel channel, Member member, Message message) {
                        String content = message.getContentRaw();
                        if (application == null) {
                            Optional<Application> application = guildSettings.getApplication(content);
                            if (application.isPresent()) {
                                this.application = application.get();
                                channel.sendMessage("Application **" + this.application.getDisplayName() + "** selected.\n"
                                        + "Please enter a name to add a user.").queue();
                                return false;
                            }
                            channel.sendMessage("Invalid application.\n"
                                    + "Please select a application.\n" + userApplicationNames).queue();
                            return false;
                        }

                        User user = parser.getGuildUser(guild, content);

                        if (user != null) {
                            application.addOwner(user);
                            configuration.save();
                            channel.sendMessage("User " + user.getAsTag() + " added."
                                    + "\nWrite another name to add or \"done\" to add the user.").queue();
                        } else {
                            channel.sendMessage("Invalid name.\nWrite a name to add or \"exit\".").queue();
                        }
                        return false;
                    }
                });
    }

    private void revokeAccess(Member member, TextChannel channel, Guild guild, GuildSettings guildSettings) {
        String userApplicationNames = getUserApplicationNames(guildSettings, member);

        dialogHandler.startDialog(guild, channel, member,
                "Please select a application.\n" + userApplicationNames,
                new Dialog() {
                    private final Set<User> users = new HashSet<>();
                    private Application application;

                    @Override
                    public boolean invoke(Guild guild, TextChannel channel, Member member, Message message) {
                        String content = message.getContentRaw();
                        if (application == null) {
                            Optional<Application> application = guildSettings.getApplication(content);
                            if (application.isPresent()) {
                                this.application = application.get();
                                channel.sendMessage("Application **" + this.application.getDisplayName() + "** selected.\n"
                                        + "Please enter a name to add a user.").queue();
                                return false;
                            }
                            channel.sendMessage("Invalid application.\n"
                                    + "Please select a application.\n" + userApplicationNames).queue();
                            return false;
                        }

                        User user = parser.getGuildUser(guild, content);

                        if (user != null) {
                            application.removeOwner(user);
                            channel.sendMessage("User " + user.getAsTag() + " removed."
                                    + "\nWrite another name to add or \"done\" to add the user.").queue();
                        } else {
                            channel.sendMessage("Invalid name.\nWrite a name to add or \"exit\".").queue();
                        }
                        return false;
                    }
                });
    }

    private void deleteUpdate(Member member, TextChannel channel, Guild guild, GuildSettings guildSettings) {
        String applicationNames = getUserApplicationNames(guildSettings, member);


        dialogHandler.startDialog(guild, channel, member,
                "Please select a application.\n" + applicationNames,
                new Dialog() {
                    private Application application = null;
                    private Release release;

                    @Override
                    public boolean invoke(Guild guild, TextChannel channel, Member member, Message message) {
                        String content = message.getContentRaw();
                        if (application == null) {
                            Optional<Application> application = guildSettings.getApplication(content);
                            if (application.isEmpty()) {
                                channel.sendMessage("This application does not exist.\n" +
                                        "For which application do you want to delete an update?\n" + applicationNames).queue();
                                return false;
                            }

                            if (!application.get().isOwner(member)) {
                                channel.sendMessage("This is not your application.\n" +
                                        "For which application do you want to delete an update?\n" + applicationNames).queue();
                                return false;
                            }

                            this.application = application.get();
                            channel.sendMessage("You selected " + this.application.getDisplayName() + ".").queue();
                            String versions = this.application.getReleases(true).stream().map(Release::version).collect(Collectors.joining(", "));
                            channel.sendMessage("Which version do you want to delete?\n" + versions).queue();
                            return false;
                        }

                        if (release == null) {
                            Optional<Release> optionalRelease = application.getRelease(content);
                            String versions = this.application.getReleases(true).stream().map(Release::version).collect(Collectors.joining(", "));
                            if (optionalRelease.isEmpty()) {
                                channel.sendMessage("Invalid release.\n Which version do you want to delete?\n" + versions).queue();
                                return false;
                            }
                            release = optionalRelease.get();
                            channel.sendMessage("Attemting to delete release " + release.version() + ".\nPlease confirm by typing \"confirm\"").queue();
                            return false;
                        }

                        if ("confirm".equalsIgnoreCase(content)) {
                            application.deleteRelease(release.version());
                            channel.sendMessage("Removed version **" + release.version() + "**.").queue();
                            return true;
                        }
                        if ("cancel".equalsIgnoreCase(content)) {
                            channel.sendMessage("Canceled deletion.").queue();
                            return true;
                        }

                        channel.sendMessage("Please write **\"confirm\"** to delete the version **"
                                            + release.version() + "** or **\"cancel\"** to cancel the deletion.").queue();
                        configuration.save();
                        return false;
                    }
                });
    }

    private void setName(TextChannel channel, String[] args, Application application) {
        if (args.length == 1) {
            channel.sendMessage("Please provide a new name.").queue();
            return;
        }

        application.setDisplayName(String.join(" ", ArgumentParser.getMessage(args, 1)));
        channel.sendMessage("Changed name to " + application.getDisplayName() + ".").queue();
        configuration.save();
    }

    private void setDescription(TextChannel channel, String[] args, Application application) {
        if (args.length == 1) {
            channel.sendMessage("Please provide a description.").queue();
            return;
        }

        application.setDescription(String.join(" ", ArgumentParser.getMessage(args, 1)));
        channel.sendMessage("Description set to " + application.getDescription() + ".").queue();
        configuration.save();
    }

    private void setAlias(TextChannel channel, String[] args, Application application) {
        if (args.length == 1) {
            channel.sendMessage("Please provide one or more aliases.").queue();
            return;
        }

        application.setAlias(Arrays.copyOfRange(args, 1, args.length - 1));
        channel.sendMessage("Aliases set to " + String.join(", ", application.getAlias()) + ".").queue();
        configuration.save();
    }

    private void setChannel(TextChannel channel, GenericGuildMessageEvent event, String[] args, Application
            application) {
        if (args.length == 1) {
            channel.sendMessage("Please provide a channel.").queue();
            return;
        }

        if ("none".equalsIgnoreCase(args[1])) {
            application.setChannel(null);
            channel.sendMessage("Removed update channel.").queue();
            return;
        }

        Optional<TextChannel> textChannel = ArgumentParser.getTextChannel(event.getGuild(), args[1]);
        if (textChannel.isEmpty()) {
            channel.sendMessage("Invalid channel.").queue();
            return;
        }

        application.setChannel(textChannel.get().getIdLong());
        channel.sendMessage("Set channel to " + textChannel.get().getAsMention()).queue();
        configuration.save();
    }

    private boolean isCommand(String receivedMessage, String[] args, GuildSettings settings, Event event) {
        boolean isCommand = false;
        if (receivedMessage.startsWith(settings.getPrefix())) {
            isCommand = true;
            //Check if the message is a command executed by a mention of the bot.
        } else if (Verifier.getIdRaw(args[0]).contentEquals(event.getJDA().getSelfUser().getId())) {
            isCommand = true;
        }
        return isCommand;
    }

    private String[] stripArgs(String receivedMessage, String[] args, GuildSettings settings, Event event) {
        String[] strippedArgs;
        if (receivedMessage.startsWith(settings.getPrefix())) {
            args[0] = args[0].substring(settings.getPrefix().length());
            strippedArgs = args;
            //Check if the message is a command executed by a mention of the bot.
        } else if (Verifier.getIdRaw(args[0]).contentEquals(event.getJDA().getSelfUser().getId())) {
            strippedArgs = Arrays.copyOfRange(args, 1, args.length);
        } else {
            strippedArgs = args;
        }
        return strippedArgs;
    }

    private String[] buildArgs(String[] args) {
        String[] newArgs;
        if (args.length > 1) {
            newArgs = Arrays.copyOfRange(args, 1, args.length);
        } else {
            newArgs = new String[0];
        }
        return newArgs;
    }

    private String getUserApplicationNames(GuildSettings guildSettings, ISnowflake snowflake) {
        return guildSettings.getApplications().values()
                .stream()
                .filter(a -> a.isOwner(snowflake))
                .map(a -> "`" + a.getIdentifier() + (a.getAlias().length != 0 ? " (" + String.join(", ", a.getAlias()) + ")" : "") + "`")
                .collect(Collectors.joining(", "));
    }

}

package de.eldoria.updatebutler.listener;

import com.google.common.hash.Hashing;
import de.eldoria.updatebutler.config.Application;
import de.eldoria.updatebutler.config.Configuration;
import de.eldoria.updatebutler.config.GuildSettings;
import de.eldoria.updatebutler.config.Release;
import de.eldoria.updatebutler.dialogue.Dialog;
import de.eldoria.updatebutler.dialogue.DialogHandler;
import de.eldoria.updatebutler.util.ArgumentParser;
import de.eldoria.updatebutler.util.C;
import de.eldoria.updatebutler.util.FileHelper;
import de.eldoria.updatebutler.util.FileUtil;
import de.eldoria.updatebutler.util.TextFormatting;
import de.eldoria.updatebutler.util.Verifier;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.message.guild.GenericGuildMessageEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class CommandListener extends ListenerAdapter {
    private static final Pattern VERSION = Pattern.compile("^[\\sa-zA-Z0-9.\\-_]+$");

    private final Configuration configuration;
    private final ArgumentParser parser;
    private final ShardManager shardManager;
    private final String[] userCommands = {"latestVersion", "versions", "versionInfo", "info", "applist"};
    private final String[] ownerCommands = {"setPrefix", "grant", "revoke", "createApp"};
    private final String[] appCommands = {"deleteApp", "grantAccess", "revokeAccess", "deployUpdate", "deleteUpdate", "setName", "setDescr", "setAlias", "setChannel"};


    private static final Pattern ID_PATTERN = Pattern.compile("(?:<[@#!&]{1,2})?(?<id>[0-9]{18})(?:>)?");

    public CommandListener(Configuration configuration, ShardManager shardManager) {
        this.configuration = configuration;
        this.parser = new ArgumentParser(shardManager);
        this.shardManager = shardManager;
    }

    private final DialogHandler dialogHandler = new DialogHandler();

    @Override
    public void onGuildMessageUpdate(@Nonnull GuildMessageUpdateEvent event) {
        handleMessage(event.getMember(), event.getChannel(), event.getGuild(), event.getMessage(), event);
    }

    @Override
    public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event) {
        handleMessage(event.getMember(), event.getChannel(), event.getGuild(), event.getMessage(), event);
    }

    public void handleMessage(Member member, TextChannel channel, Guild guild, Message message, GenericGuildMessageEvent event) {

        GuildSettings guildSettings = configuration.getGuildSettings(guild.getId());

        String receivedMessage = message.getContentRaw();
        receivedMessage = receivedMessage.replaceAll("\\s\\s+", " ");
        String[] args = receivedMessage.split(" ");

        if (!isCommand(receivedMessage, args, guildSettings, event)) {
            dialogHandler.invoke(guild, channel, member, message);
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
                return;
            }

            if ("versions".equalsIgnoreCase(label)) {
                versions(channel, args, application);
                return;
            }
            if ("versionInfo".equalsIgnoreCase(label)) {
                versionInfo(channel, event, args, application);
                return;
            }

            if ("info".equalsIgnoreCase(label)) {
                channel.sendMessage(application.getApplicationInfo(configuration, event.getGuild(), parser)).queue();
                return;
            }

            // latestVersion <application> -> Get the latest version of a plugin
            // versions <application> -> List of versions
            // versionInfo <application> <version> -> Info of a version
            // getVersion <application> <version> -> get the version
            // info <application> <version> -> get the version
            // appList -> get the version
            // <application or alias> -> information about a application
            return;
        }

        // Owner Commands
        if (isInArray(label, ownerCommands)) {
            if (!guildSettings.isAllowedUser(member) && !member.isOwner()) {
                channel.sendMessage("You are not a bot owner!").queue();
                return;
            }

            if ("setPrefix".equalsIgnoreCase(label)) {
                setPrefix(channel, guildSettings, args);
                return;
            }

            if ("grant".equalsIgnoreCase(label)) {
                grant(channel, event, guildSettings, args);
                return;
            }

            if ("revoke".equalsIgnoreCase(label)) {
                revoke(member, channel, event, guildSettings, args);
                return;
            }

            if ("createApp".equalsIgnoreCase(label)) {
                createApp(member, channel, guild, event, guildSettings);
                return;
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

            if ("deleteApp".equalsIgnoreCase(label)) {
                deleteApp(member, channel, guild, guildSettings, args[0], application);
                return;
            }

            if ("grantAccess".equalsIgnoreCase(label)) {
                grantAccess(channel, event, args, application);
                return;
            }

            if ("revokeAccess".equalsIgnoreCase(label)) {
                revokeAccess(member, channel, event, args, application);
                return;
            }


            if ("deleteUpdate".equalsIgnoreCase(label)) {
                deleteUpdate(member, channel, guild, args, application);
                return;
            }

            if ("setName".equalsIgnoreCase(label)) {
                setName(channel, args, application);
                return;
            }

            if ("setDescr".equalsIgnoreCase(label)) {
                setDescription(channel, args, application);
                return;
            }

            if ("setAlias".equalsIgnoreCase(label)) {
                setAlias(channel, args, application);
                return;
            }

            if ("setChannel".equalsIgnoreCase(label)) {
                setChannel(channel, event, args, application);
                return;
            }
            return;
        }
        Optional<Application> optional = guildSettings.getApplication(label);

        if (optional.isEmpty()) {
            channel.sendMessage("Invalid command.").queue();
            return;
        }

        channel.sendMessage(optional.get().getApplicationInfo(configuration, event.getGuild(), parser)).queue();
    }

    private void createApp(Member member, TextChannel channel, Guild guild, GenericGuildMessageEvent event, GuildSettings guildSettings) {
        channel.sendMessage("Please enter the application identifier.").queue();

        dialogHandler.startDialog(guild, channel, member, new Dialog() {
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
                    var textChannel = ArgumentParser.getTextChannel(event.getGuild(), content);
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

    private void revoke(Member member, TextChannel channel, GenericGuildMessageEvent event, GuildSettings guildSettings, String[] args) {
        if (args.length == 0) {
            channel.sendMessage("revoke <users...>").queue();
            return;
        }

        List<Member> guildMembers = parser.getGuildMembers(event.getGuild(), Arrays.asList(args));

        for (Member guildMember : guildMembers) {
            if (Verifier.equalSnowflake(guildMember, member)) continue;
            guildSettings.removeAllowedUser(guildMember);
        }

        String names = guildMembers.stream().map(m -> "**" + m.getEffectiveName() + "**").collect(Collectors.joining(", "));

        channel.sendMessage("Revoked bot usage from: " + names).queue();
        configuration.save();
    }

    private void grant(TextChannel channel, GenericGuildMessageEvent event, GuildSettings guildSettings, String[] args) {
        if (args.length != 1) {
            channel.sendMessage("grant <users...>").queue();
            return;
        }

        List<Member> guildMembers = parser.getGuildMembers(event.getGuild(), Arrays.asList(args));

        for (Member guildMember : guildMembers) {
            guildSettings.addAllowedUser(guildMember);
        }

        String names = guildMembers.stream().map(m -> "**" + m.getEffectiveName() + "**").collect(Collectors.joining(", "));

        channel.sendMessage("Granted bot usage to: " + names).queue();
        configuration.save();
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
                    release.getVersion(),
                    C.DATE_FORMAT.format(release.getPublished()),
                    String.valueOf(release.getDownloads()));
        }

        channel.sendMessage("Versions of " + application.getDisplayName() + "\n"
                + tableBuilder.toString()
                + "and " + Math.max(releases.size() - 10, 0) + " more.").queue();
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
        Release release;
        if (dev) {
            release = application.getLatestVersion();
        } else {
            release = application.getLatestStableVersion();
            if (release == null) {
                release = application.getLatestVersion();
            }
        }
        if (release == null) {
            channel.sendMessage("No releases published for this application").queue();
            return;
        }

        MessageEmbed releaseInfo = application.getReleaseInfo(configuration, parser, event.getGuild(), release);
        channel.sendMessage(releaseInfo).queue();
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
        channel.sendMessage(builder.build()).queue();
    }

    private void deleteApp(Member member, TextChannel channel, Guild guild, GuildSettings guildSettings, String arg, Application application) {
        final String applicationName = arg;
        guildSettings.removeApplication(applicationName);
        channel.sendMessage("Please write **\"confirm\"** to delete the application **"
                + application.getDisplayName() + "** or **\"cancel\"** to cancel the deletion.").queue();

        dialogHandler.startDialog(guild, channel, member, (g, c, mem, mes) -> {
            String content = mes.getContentRaw();
            if ("confirm".equalsIgnoreCase(content)) {
                guildSettings.removeApplication(applicationName);
                c.sendMessage("Removed application **" + application.getDisplayName() + "**.").queue();
                return true;
            }
            if ("cancel".equalsIgnoreCase(content)) {
                c.sendMessage("Canceled deletion.").queue();
                return true;
            }
            c.sendMessage("Please write **\"confirm\"** to delete the application **"
                    + application.getDisplayName() + "** or **\"cancel\"** to cancel the deletion.").queue();
            configuration.save();
            return false;
        });
    }

    private void grantAccess(TextChannel channel, GenericGuildMessageEvent event, String[] args, Application application) {
        if (args.length != 1) {
            channel.sendMessage("grantAccess <application> <users...>").queue();
            return;
        }

        List<Member> guildMembers = parser.getGuildMembers(event.getGuild(), Arrays.asList(args));

        for (Member guildMember : guildMembers) {
            application.addOwner(guildMember.getUser());
        }

        String names = guildMembers.stream().map(m -> "**" + m.getEffectiveName() + "**").collect(Collectors.joining(", "));

        channel.sendMessage("Granted application access to: " + names).queue();
        configuration.save();
    }

    private void revokeAccess(Member member, TextChannel channel, GenericGuildMessageEvent event, String[] args, Application application) {
        if (args.length == 1) {
            channel.sendMessage("revokeAccess <application> <users...>").queue();
            return;
        }

        List<Member> guildMembers = parser.getGuildMembers(event.getGuild(), Arrays.asList(args));

        for (Member guildMember : guildMembers) {
            if (Verifier.equalSnowflake(guildMember, member)) {
                continue;
            }
            application.removeOwner(guildMember.getUser());
        }

        String names = guildMembers.stream().map(m -> "**" + m.getEffectiveName() + "**").collect(Collectors.joining(", "));

        channel.sendMessage("Revoked application access from: " + names).queue();
        configuration.save();
    }

    private void setChannel(TextChannel channel, GenericGuildMessageEvent event, String[] args, Application application) {
        if (args.length == 1) {
            channel.sendMessage("Please provide a channel.").queue();
            return;
        }

        Optional<TextChannel> textChannel = ArgumentParser.getTextChannel(event.getGuild(), args[1]);
        if (textChannel.isEmpty()) {
            channel.sendMessage("Invalid channel.").queue();
            return;
        }

        application.setChannel(textChannel.get().getIdLong());
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

    private void setDescription(TextChannel channel, String[] args, Application application) {
        if (args.length == 1) {
            channel.sendMessage("Please provide a description.").queue();
            return;
        }

        application.setDescription(String.join(" ", ArgumentParser.getMessage(args, 1)));
        channel.sendMessage("Description set to " + application.getDescription() + ".").queue();
        configuration.save();
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

    private void deleteUpdate(Member member, TextChannel channel, Guild guild, String[] args, Application application) {
        if (args.length == 1) {
            channel.sendMessage("Please provide a version.").queue();
            return;
        }
        channel.sendMessage("Please write **\"confirm\"** to delete the application **"
                + application.getDisplayName() + "** or **\"cancel\"** to cancel the deletion.").queue();

        String version = args[1];

        Optional<Release> release = application.getRelease(version);
        if (release.isEmpty()) {
            channel.sendMessage("Version not found.").queue();
            return;
        }

        dialogHandler.startDialog(guild, channel, member, (g, c, mem, mes) -> {
            String content = mes.getContentRaw();
            if ("confirm".equalsIgnoreCase(content)) {
                application.deleteRelease(version);
                channel.sendMessage("Removed version **" + version + "**.").queue();
                return true;
            }
            if ("cancel".equalsIgnoreCase(content)) {
                channel.sendMessage("Canceled deletion.").queue();
                return true;
            }

            channel.sendMessage("Please write **\"confirm\"** to delete the version **"
                    + version + "** or **\"cancel\"** to cancel the deletion.").queue();
            configuration.save();
            return false;
        });
    }

    private void deployUpdate(Member member, TextChannel channel, Guild guild, GuildSettings guildSettings) {
        String applicationNames = guildSettings.getApplications().values()
                .stream()
                .filter(a -> a.isOwner(member))
                .map(a -> "`" + a.getIdentifier() + (a.getAlias().length != 0 ? " (" + a.getAlias()[0] + ")" : "") + "`")
                .collect(Collectors.joining(", "));
        channel.sendMessage("For which application do you want to deploy an update?\n" + applicationNames).queue();

        dialogHandler.startDialog(guild, channel, member, new Dialog() {
            private Application application;
            private String version;
            private String title;
            private String patchnotes;
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

                if (patchnotes == null) {
                    patchnotes = content;
                    channel.sendMessage("Patchnotes set to:\n" + patchnotes).queue();
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

                Optional<File> optFile = FileHelper.getFileFromURL(attachment.getUrl());

                if (optFile.isEmpty()) {
                    channel.sendMessage("Failed to download file.").queue();
                    return false;
                }

                File source = optFile.get();

                boolean exists = source.exists();

                byte[] bytes;
                try {
                    bytes = FileUtils.readFileToByteArray(source);
                } catch (IOException e) {
                    channel.sendMessage("Could not create checksum.").queue();
                    log.error("Failed to create hash from file.", e);
                    return false;
                }

                Matcher matcher = C.FILE_NAME.matcher(source.getName());
                if (!matcher.find()) {
                    channel.sendMessage("Could not parse file name").queue();
                    return false;
                }

                Path resources;
                try {
                    resources = Files.createDirectories(Paths.get(FileUtil.home(), "resources",
                            Integer.toString(application.getId()),
                            version));
                } catch (IOException e) {
                    log.error("Could not create version file");
                    channel.sendMessage("An error occured").queue();
                    return false;
                }

                String hash = Hashing.sha256().hashBytes(bytes).toString();
                Path targetPath = Paths.get(resources.toString(),
                        application.getIdentifier() + "." + matcher.group(2));
                File target = targetPath.toFile();
                try {
                    Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    channel.sendMessage("Something went wrong while saving.").queue();
                    log.warn("Failed to save file.", e);
                    return false;
                }
                String publishedDate = C.DATE_FORMAT.format(LocalDateTime.now().atZone(ZoneId.of("UCT")));
                Release release = new Release(version, title, patchnotes, devBuild,
                        publishedDate, target.toString(), hash);
                channel.sendMessage("Created new release!").queue();
                channel.sendMessage(application.getReleaseInfo(configuration, parser, guild, release)).queue();

                application.addRelease(version, release);
                configuration.save();
                return true;
            }
        });
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

    public static boolean isInArray(String s, String... sA) {
        for (String s1 : sA) {
            if (s1.equalsIgnoreCase(s)) return true;
        }
        return false;
    }

}

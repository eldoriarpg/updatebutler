package de.eldoria.updatebutler.config;

import com.google.common.hash.Hashing;
import com.google.gson.annotations.SerializedName;
import de.eldoria.updatebutler.UpdateButler;
import de.eldoria.updatebutler.util.ArgumentParser;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class Application {
    @Getter
    @Setter
    private int id;
    @Getter
    @Setter
    private String identifier;
    @Getter
    @Setter
    @SerializedName("display_name")
    private String displayName;
    @Getter
    @Setter
    private String description;

    @Getter
    @Setter
    private String[] alias;

    private Set<Long> owner;
    @Getter
    private String webhook;

    @Getter
    @Setter
    private Long channel;
    private HashMap<String, Release> releases = new HashMap<>();

    public Application(int id, String identifier, String displayName, String description, String[] alias, Long owner, Long channel) {
        this.id = id;
        this.identifier = identifier;
        this.displayName = displayName;
        this.description = description;
        this.alias = alias;
        this.owner = new HashSet<>(Collections.singletonList(owner));
        this.channel = channel;
        this.webhook = Hashing.sha256()
                .hashString(displayName + Instant.now().toEpochMilli(), StandardCharsets.UTF_8)
                .toString().toLowerCase();
    }

    public boolean isOwner(ISnowflake user) {
        return owner.contains(user.getIdLong());
    }

    public boolean addOwner(ISnowflake user) {
        return owner.add(user.getIdLong());
    }

    public boolean removeOwner(ISnowflake user) {
        return owner.remove(user.getIdLong());
    }

    public void addRelease(String key, Release release) {
        UpdateButler.getReleaseCreateListener().onReleaseCreation(this, release);
        releases.put(key, release);
    }

    public Optional<Release> getRelease(String key) {
        if (key.equalsIgnoreCase("latest")) {
            return Optional.ofNullable(getReleases(true).get(0));
        }

        for (var release : releases.entrySet()) {
            if (release.getKey().equalsIgnoreCase(key)) {
                return Optional.ofNullable(release.getValue());
            }
        }
        return Optional.empty();
    }

    public boolean deleteRelease(String key) {
        return releases.remove(key) != null;
    }

    public MessageEmbed getReleaseInfo(Configuration configuration, ArgumentParser parser, Guild guild, Release release) {
        String owner = parser.getGuildMembers(guild, this.owner
                .stream().
                        map(Object::toString).
                        collect(Collectors.toList()))
                .stream()
                .map(Member::getAsMention)
                .collect(Collectors.joining(", "));

        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("#" + id + " " + getDisplayName() + " " + release.getVersion())
                .setDescription(getDescription())
                .addField("Patchnotes", release.getPatchnotes(), false)
                .addField("Owner", owner, true)
                .addField("Download", configuration.getHostName() + "/download?id=" + id + "&version=" + release.getVersion().replace(" ", "_"), true)
                .addField("Checksum Sha256", release.getChecksum(), true)
                .setTimestamp(release.getPublished());
        return builder.build();
    }

    public MessageEmbed getApplicationInfo(Configuration configuration, Guild guild, ArgumentParser parser) {
        Release latestVersion = getLatestStableVersion();

        String owner = parser.getGuildMembers(guild, this.owner
                .stream().
                        map(Object::toString).
                        collect(Collectors.toList()))
                .stream()
                .map(Member::getAsMention)
                .collect(Collectors.joining(", "));

        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("#" + id + " " + getDisplayName())
                .setDescription(getDescription())
                .addField("Owner", owner, true);

        if (alias.length != 0) {
            builder.addField("Alias", String.join(", ", alias), true);
        }

        if (latestVersion != null) {
            builder.addField("Latest Version", latestVersion.getVersion(), true)
                    .addField("Download", configuration.getHost() + latestVersion.getFile(), true)
                    .addField("Checksum Sha256", latestVersion.getChecksum(), true);
            builder.setTimestamp(latestVersion.getPublished());
        }

        return builder.build();
    }

    public Release getLatestStableVersion() {
        if (releases.isEmpty()) return null;
        return releases.values().stream().filter(r -> !r.isDevBuild()).sorted(Comparator.comparing(Release::getPublished)).collect(Collectors.toList()).get(0);
    }

    public Release getLatestVersion() {
        if (releases.isEmpty()) return null;
        return releases.values().stream().sorted(Comparator.comparing(Release::getPublished)).collect(Collectors.toList()).get(0);
    }

    public List<Release> getReleases(boolean dev) {
        List<Release> collect = releases.values()
                .stream()
                .filter(r -> dev || !r.isDevBuild())
                .sorted(Comparator.comparing(Release::getPublished))
                .collect(Collectors.toList());
        Collections.reverse(collect);
        return collect;
    }
}

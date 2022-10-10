package de.eldoria.updatebutler.config;

import de.eldoria.updatebutler.util.ArgumentParser;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Data
public class Application {
    private final Set<Long> owner;
    private final String webhook;
    private final HashMap<String, Release> releases = new HashMap<>();
    /**
     * The id of the application. immutable
     */
    private int id;
    /**
     * A unique string identifier for this application. mutable.
     */
    private String identifier;
    /**
     * The display name of the application
     */
    private String displayName;
    /**
     * Short description of the application
     */
    private String description;
    private Set<String> alias;
    private Long channel;

    public Application(int id, String identifier, String displayName, String description, Set<String> alias, Set<Long> owner, Long channel, String webhook) {
        this.id = id;
        this.identifier = identifier;
        this.displayName = displayName;
        this.description = description;
        this.alias = alias;
        this.owner = owner;
        this.channel = channel;
        this.webhook = webhook;
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
        releases.put(key, release);
    }

    public Optional<Release> getRelease(String key) {
        if ("latest".equalsIgnoreCase(key)) {
            return Optional.ofNullable(getReleases(true).get(0));
        }

        for (var release : releases.entrySet()) {
            if (release.getKey().equalsIgnoreCase(key.replace("_", " "))) {
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
                .setTitle("#" + id + " " + getDisplayName() + " " + release.version())
                .setDescription(release.title())
                .addField("Patchnotes", release.patchnotes(), false)
                .addField("Stable", release.isDevBuild() ? "dev" : "stable", true)
                .addField("Download", configuration.getHostName() + "/download?id=" + id + "&version=" + release.version().replace(" ", "_"), true)
                .addField("Checksum Sha256", release.checksum(), true)
                .setTimestamp(release.published());
        return builder.build();
    }

    public MessageEmbed getApplicationInfo(Configuration configuration, Guild guild, ArgumentParser parser) {
        Optional<Release> optionalRelease = getLatestStableVersion();
        if (optionalRelease.isEmpty()) {
            optionalRelease = getLatestVersion();
        }

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

        if (!alias.isEmpty()) {
            builder.addField("Alias", String.join(", ", alias), true);
        }

        if (optionalRelease.isPresent()) {
            Release release = optionalRelease.get();
            builder.addField("Latest Version", release.version(), true)
                    .addField("Download", configuration.getHostName() + "/download?id=" + id + "&version=" + release.version().replace(" ", "_"), true)
                    .addField("Checksum Sha256", release.checksum(), true);
            builder.setTimestamp(release.published());
        }

        return builder.build();
    }

    public Optional<Release> getLatestStableVersion() {
        if (releases.isEmpty()) return Optional.empty();
        return releases.values().stream()
                .filter(r -> !r.isDevBuild())
                .max(Comparator.comparing(Release::published));
    }

    /**
     * Get the latest release including dev builds
     *
     * @return latest release
     */
    public Optional<Release> getLatestVersion() {
        if (releases.isEmpty()) return Optional.empty();
        return releases.values().stream().max(Comparator.comparing(Release::published));
    }

    /**
     * Get all releases of a application
     *
     * @param dev true when dev builds should be included
     * @return list of dev builds.
     */
    public List<Release> getReleases(boolean dev) {
        List<Release> collect = releases.values()
                .stream()
                .filter(r -> dev || !r.isDevBuild())
                .sorted(Comparator.comparing(Release::published))
                .collect(Collectors.toList());
        Collections.reverse(collect);
        return Collections.unmodifiableList(collect);
    }

    public void setReleases(List<Release> releases) {
        for (Release release : releases) {
            this.releases.put(release.version(), release);
        }
    }
}

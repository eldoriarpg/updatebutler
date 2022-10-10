package de.eldoria.updatebutler.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import de.eldoria.updatebutler.config.commands.EmbedCommand;
import de.eldoria.updatebutler.config.commands.PlainCommand;
import de.eldoria.updatebutler.config.phrase.PlainPhrase;
import de.eldoria.updatebutler.config.phrase.RegexPhrase;
import de.eldoria.updatebutler.listener.ReleaseCreateListener;
import de.eldoria.updatebutler.util.FileUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Optional;

import static de.eldoria.updatebutler.util.FileUtil.home;

@Slf4j
@Data
public class Configuration {

    private static final ObjectMapper mapper;
    private String token = "";
    private String hostName = "";
    private String host = "";
    private int port = 19050;
    private HashMap<String, GuildSettings> guildSettings = new HashMap<>();
    @JsonIgnore
    private ReleaseCreateListener listener;
    private int currentId = 0;
    private DBSettings dbSettings = new DBSettings();

    static {
        mapper = new ObjectMapper()
                .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .setDefaultPrettyPrinter(new DefaultPrettyPrinter());
        mapper.registerSubtypes(new NamedType(EmbedCommand.class, "embed_command"));
        mapper.registerSubtypes(new NamedType(PlainCommand.class, "plain_command"));
        mapper.registerSubtypes(new NamedType(PlainPhrase.class, "plain_phrase"));
        mapper.registerSubtypes(new NamedType(RegexPhrase.class, "regex_phrase"));
    }

    public static Configuration load() throws IOException {
        File config = FileUtil.createDirectory("config");
        try (var in = ClassLoader.getSystemClassLoader().getResourceAsStream("config.json")) {
            var file = FileUtil.createFile(in, "/config/config.json");
            return mapper.readValue(file, Configuration.class);
        }
    }

    public GuildSettings getGuildSettings(String id) {
        if (!this.guildSettings.containsKey(id)) {
            this.guildSettings.put(id, new GuildSettings());
            save();
        }
        return guildSettings.get(id);
    }

    public void save() {
        try {
            mapper.writeValue(Paths.get(home(), "/config/config.json").toFile(), this);
        } catch (IOException e) {
            log.warn("Could not save config", e);
        }
    }

    public int getNextAppId() {
        currentId++;
        save();
        return currentId;
    }

    public Optional<Application> getApplicationById(int id) {
        for (GuildSettings value : guildSettings.values()) {
            Application application = value.getApplications().get(Integer.toString(id));
            if (application != null) {
                return Optional.of(application);
            }
        }
        return Optional.empty();
    }

    public Optional<Application> getApplicationByWebhook(String hash) {
        for (GuildSettings value : guildSettings.values()) {
            for (Application application : value.getApplications().values()) {
                if (application.getWebhook().equals(hash)) {
                    return Optional.of(application);
                }
            }
        }
        return Optional.empty();
    }

    public void setReleaseListener(ReleaseCreateListener listener) {
        this.listener = listener;
    }

    public void addRelease(Application application, Release release) {
        application.addRelease(release.version(), release);
        listener.onReleaseCreation(application, release);
        save();
    }
}

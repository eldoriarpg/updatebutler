package de.eldoria.updatebutler.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import de.eldoria.updatebutler.config.commands.UserCommand;
import de.eldoria.updatebutler.config.phrase.Phrase;
import de.eldoria.updatebutler.config.util.GsonAdapter;
import de.eldoria.updatebutler.listener.ReleaseCreateListener;
import de.eldoria.updatebutler.util.FileUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Optional;

import static de.eldoria.updatebutler.util.FileUtil.home;

@Slf4j
public class Configuration {

    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .excludeFieldsWithoutExposeAnnotation()
            .setPrettyPrinting()
            .registerTypeAdapter(UserCommand.class,
                    new GsonAdapter<UserCommand>("de.eldoria.updatebutler.config.commands"))
            .registerTypeAdapter(Phrase.class,
                    new GsonAdapter<Phrase>("de.eldoria.updatebutler.config.phrase"))
            .create();
    @Getter
    @Expose
    private final String token = "";
    @Getter
    @Expose
    private final String hostName = "";
    @Getter
    @Expose
    private final String host = "";
    @Getter
    @Expose
    private final int port = 19050;
    @SerializedName("guildSettings")
    @Expose
    @Getter
    private final HashMap<String, GuildSettings> guildSettings = new HashMap<>();
    private ReleaseCreateListener listener;
    @Expose
    private int currentId = 0;
    @Expose
    @Getter
    private DBSettings dbSettings;

    public static Configuration load() throws IOException {
        File config = FileUtil.createDirectory("config");
        try (var in = ClassLoader.getSystemClassLoader().getResourceAsStream("config.json")) {
            var file = FileUtil.createFile(in, "/config/config.json");
            try (JsonReader reader = new JsonReader(new FileReader(file))) {
                return GSON.fromJson(reader, Configuration.class);
            }
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
        try (var a = new FileWriter(Paths.get(home(), "/config/config.json").toFile())) {
            GSON.toJson(this, a);
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
        application.addRelease(release.getVersion(), release);
        listener.onReleaseCreation(application, release);
        save();
    }
}

package de.eldoria.updatebutler.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
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

    private static final Gson GSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    private ReleaseCreateListener listener;
    @Getter
    @Expose
    private String token = "";

    @Expose
    private int currentId = 0;

    @Getter
    @Expose
    private String hostName = "";

    @Getter
    @Expose
    private String host = "";

    @Getter
    @Expose
    private int port = 19050;

    @SerializedName("guildSettings")
    @Expose
    private HashMap<String, GuildSettings> guildSettings = new HashMap<>();

    public static Configuration load() throws IOException {
        File config = FileUtil.createDirectory("config");
        try (var in = ClassLoader.getSystemClassLoader().getResourceAsStream("config.json")) {
            var file = FileUtil.createFile(in, "/config/config.json");
            try (JsonReader reader = new JsonReader(new FileReader(file))) {
                return new Gson().fromJson(reader, Configuration.class);
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

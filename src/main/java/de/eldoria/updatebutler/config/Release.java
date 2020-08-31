package de.eldoria.updatebutler.config;

import com.google.gson.annotations.SerializedName;
import de.eldoria.updatebutler.util.C;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Release {
    private String version;
    private String title;
    private int downloads;
    private String patchnotes;
    @SerializedName("dev_build")
    private boolean devBuild;
    private String published;
    private String file;
    private String checksum;

    public Release(String version, String title, String patchnotes, boolean devBuild, String published, String file, String checksum) {
        this.version = version;
        this.title = title;
        this.patchnotes = patchnotes;
        this.devBuild = devBuild;
        this.published = published;
        this.file = file;
        this.checksum = checksum;
    }

    public LocalDateTime getPublished() {
        return LocalDateTime.parse(published, C.DATE_FORMAT);
    }

    public void downloaded() {
        downloads++;
    }
}

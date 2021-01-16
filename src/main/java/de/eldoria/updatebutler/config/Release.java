package de.eldoria.updatebutler.config;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import de.eldoria.updatebutler.util.C;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;

@Data
public class Release {

    @Expose
    private String version;
    @Expose
    private String title;
    @Expose
    private String patchnotes;
    @SerializedName("dev_build")
    @Expose
    private boolean devBuild;
    @Expose
    private String published;
    @Expose
    private String file;
    @Expose
    private String checksum;
    @Expose
    private int downloads;

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

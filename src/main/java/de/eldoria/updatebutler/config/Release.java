package de.eldoria.updatebutler.config;

import java.time.LocalDateTime;

public class Release {
    private String version;
    private String title;
    private String patchnotes;
    private boolean devBuild;
    private LocalDateTime published;
    private String file;
    private String checksum;
    private int downloads;

    public Release(String version, String title, String patchnotes, boolean devBuild, LocalDateTime published, String file, String checksum, int downloads) {
        this.version = version;
        this.title = title;
        this.patchnotes = patchnotes;
        this.devBuild = devBuild;
        this.published = published;
        this.file = file;
        this.checksum = checksum;
        this.downloads = downloads;
    }


    public void downloaded() {
        downloads++;
    }

    public String version() {
        return this.version;
    }

    public String title() {
        return this.title;
    }

    public String patchnotes() {
        return this.patchnotes;
    }

    public boolean isDevBuild() {
        return this.devBuild;
    }

    public LocalDateTime published() {
        return this.published;
    }

    public String file() {
        return this.file;
    }

    public String checksum() {
        return this.checksum;
    }

    public int downloads() {
        return this.downloads;
    }
}

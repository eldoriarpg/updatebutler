package de.eldoria.updatebutler.api.debug.data;

import lombok.Getter;

@Getter
public class EntryData {
    protected String name;
    protected String content;

    public EntryData(String name, String content) {
        this.name = name;
        this.content = content;
    }

    @Override
    public String toString() {
        return "Path: " + name + "\n\n" + content;
    }
}

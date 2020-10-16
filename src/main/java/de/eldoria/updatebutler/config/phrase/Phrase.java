package de.eldoria.updatebutler.config.phrase;

import com.google.gson.annotations.Expose;
import lombok.Getter;

@Getter
public abstract class Phrase {
    @Expose
    private final String phrase;
    @Expose
    private final String command;

    protected Phrase(String phrase, String command) {
        this.phrase = phrase;
        this.command = command;
    }

    public abstract boolean matches(String string);
}

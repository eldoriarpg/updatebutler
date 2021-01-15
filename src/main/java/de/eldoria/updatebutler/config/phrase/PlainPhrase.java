package de.eldoria.updatebutler.config.phrase;

import com.google.gson.annotations.Expose;

public class PlainPhrase extends Phrase {
    @Expose
    boolean caseSensitive;

    public PlainPhrase(String phrase, String command, boolean caseSensitive) {
        super(phrase, command);
        this.caseSensitive = caseSensitive;
    }

    @Override
    public boolean matches(String string) {
        if (caseSensitive) {
            return string.contains(getPhrase());
        }
        return string.toLowerCase().contains(getPhrase().toLowerCase());
    }
}

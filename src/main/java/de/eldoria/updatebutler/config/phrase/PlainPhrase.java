package de.eldoria.updatebutler.config.phrase;


public class PlainPhrase extends Phrase {
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

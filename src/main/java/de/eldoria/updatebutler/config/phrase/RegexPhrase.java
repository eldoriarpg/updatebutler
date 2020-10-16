package de.eldoria.updatebutler.config.phrase;

import com.google.gson.annotations.Expose;

import java.util.regex.Pattern;

public class RegexPhrase extends Phrase {
    private Pattern pattern = null;
    public RegexPhrase(String phrase, String command) {
        super(phrase, command);
    }

    @Override
    public boolean matches(String string) {
        if (pattern == null) {
            pattern = Pattern.compile(getPhrase(), Pattern.MULTILINE);
        }
        return pattern.matcher(string).find();
    }
}

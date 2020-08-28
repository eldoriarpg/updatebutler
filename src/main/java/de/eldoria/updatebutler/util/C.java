package de.eldoria.updatebutler.util;

import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public class C {
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final Pattern FILE_NAME = Pattern.compile("/?(.+?)\\.([a-z]+?)$");

    public static boolean isInArray(String s, String... sA) {
        for (String s1 : sA) {
            if (s1.equalsIgnoreCase(s)) return true;
        }
        return false;
    }
}
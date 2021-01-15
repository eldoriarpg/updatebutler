package de.eldoria.updatebutler.util;

import com.google.api.client.util.IOUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public final class FileHelper {
    private static final Pattern FILE_NAME = Pattern.compile("/?(.+?)\\.([a-z]+?)$");

    private FileHelper() {
    }

    /**
     * Get every file from a url.
     *
     * @param url url for download
     *
     * @return file object or null if the url could not be found.
     */
    public static Optional<File> getFileFromURL(String url) {
        try {
            InputStream inputStream = new URL(url).openStream();

            Matcher matcher = FILE_NAME.matcher(url);
            if (!matcher.find()) {
                return Optional.empty();
            }

            File tempFile = File.createTempFile(matcher.group(1), "." + matcher.group(2));
            FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
            IOUtils.copy(inputStream, fileOutputStream);
            tempFile.deleteOnExit();
            return Optional.of(tempFile);
        } catch (IOException e) {
            log.error("failed to fetch url {}", url, e);
        }
        return Optional.empty();
    }
}

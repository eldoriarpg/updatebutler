package de.eldoria.updatebutler.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class FileUtil {

    public static File createDirectory(String dir) {
        Path configFile = Paths.get(home(), dir);

        // Make sure that the messages directory exists.
        if (!configFile.toFile().exists()) {
            boolean mkdir = configFile.toFile().mkdir();
            if (!mkdir) {
                log.warn("Failed to create directory {}", configFile.toString());
                return null;
            }
            return configFile.toFile();
        }
        return configFile.toFile();
    }

    public static File createFile(InputStream inputStream, String destination) throws IOException {
        try {
            Files.copy(inputStream, Paths.get(home(), destination));
        } catch (FileAlreadyExistsException e) {
            return Paths.get(home(), destination).toFile();
        }
        return Paths.get(home(), destination).toFile();
    }

    public static String home() {
        return new File(".").getAbsoluteFile().getParentFile().toString();
    }
}

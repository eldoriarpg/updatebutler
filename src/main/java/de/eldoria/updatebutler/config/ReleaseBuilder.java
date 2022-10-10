package de.eldoria.updatebutler.config;

import com.google.common.hash.Hashing;
import de.eldoria.updatebutler.util.C;
import de.eldoria.updatebutler.util.FileHelper;
import de.eldoria.updatebutler.util.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.regex.Matcher;

@Slf4j
public final class ReleaseBuilder {
    private ReleaseBuilder() {
    }

    public static Optional<Release> buildRelease(Application application, String version, String name, String descr, String url, boolean dev) {
        Optional<File> optFile = FileHelper.getFileFromURL(url);

        if (optFile.isEmpty()) {
            log.error("Failed to download file.");
            return Optional.empty();
        }

        File source = optFile.get();

        boolean exists = source.exists();

        Path resources;
        try {
            resources = Files.createDirectories(Paths.get(FileUtil.home(), "resources",
                    Integer.toString(application.getId()),
                    version));
        } catch (IOException e) {
            log.error("Could not create version file");
            return Optional.empty();
        }

        Matcher matcher = C.FILE_NAME.matcher(source.getName());
        if (!matcher.find()) {
            log.info("Could not parse file name {}", source.getName());
            return Optional.empty();
        }

        String hash;
        try {
            hash = Hashing.sha256().hashBytes(FileUtils.readFileToByteArray(source)).toString();
        } catch (IOException e) {
            log.error("Failed to create hash from file.", e);
            return Optional.empty();
        }

        Path targetPath = Paths.get(resources.toString(),
                application.getIdentifier() + "." + matcher.group(2));
        try {
            Files.copy(source.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("Failed to save file.", e);
            return Optional.empty();
        }
        String publishedDate = C.DATE_FORMAT.format(LocalDateTime.now().atZone(ZoneId.of("UCT")));

        Release release = new Release(version, name, descr, dev,
                LocalDateTime.now(), targetPath.toString(), hash);
        return Optional.of(release);
    }
}

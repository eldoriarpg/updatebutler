package de.eldoria.updatebutler.api;

import com.google.api.client.http.HttpStatusCodes;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.eldoria.updatebutler.config.Application;
import de.eldoria.updatebutler.config.Configuration;
import de.eldoria.updatebutler.config.Release;
import de.eldoria.updatebutler.util.C;
import de.eldoria.updatebutler.util.FileHelper;
import de.eldoria.updatebutler.util.FileUtil;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.apache.commons.io.FileUtils;

import javax.servlet.http.HttpServletResponse;
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
import java.util.stream.Collectors;

import static spark.Spark.before;
import static spark.Spark.get;
import static spark.Spark.ipAddress;
import static spark.Spark.options;
import static spark.Spark.port;
import static spark.Spark.post;

@Slf4j
public class UpdatesAPI {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private final Configuration configuration;

    public UpdatesAPI(Configuration configuration) {
        this.configuration = configuration;
        port(configuration.getPort());
        ipAddress(configuration.getHost());

        options("/*", (request, response) -> {
            String accessControlRequestHeaders = request
                    .headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                response.header("Access-Control-Allow-Headers",
                        "Authorization");
            }

            String accessControlRequestMethod = request
                    .headers("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                response.header("Access-Control-Allow-Methods",
                        "HEAD, GET, OPTIONS");
            }

            return "OK";
        });

        before((request, response) -> {
            log.debug("Received request on route: {}\nHeaders:\n{}\nBody:\n{}",
                    request.requestMethod() + " " + request.uri(),
                    request.headers().stream().map(h -> "   " + h + ": " + request.headers(h))
                            .collect(Collectors.joining("\n")),
                    request.body());
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Headers", "*");
            //response.type("application/json");
        });

        get("/check", (((request, response) -> {
            UpdateCheckPayload updatePayload = GSON.fromJson(request.body(), UpdateCheckPayload.class);

            Optional<Application> application = configuration.getApplicationById(updatePayload.getApplicationId());

            if (application.isEmpty()) {
                response.body(GSON.toJson(new UpdateCheckResponse(false, null, null)));
                return HttpStatusCodes.STATUS_CODE_BAD_REQUEST;
            }

            Optional<Release> optionalRelease = application.get().getRelease(updatePayload.getVersion());

            if (optionalRelease.isEmpty()) {
                response.body(GSON.toJson(new UpdateCheckResponse(false, null, null)));
                return HttpStatusCodes.STATUS_CODE_OK;
            }

            Release latestRelease;

            if (updatePayload.isAllowDevBuilds()) {
                latestRelease = application.get().getLatestVersion();
            } else {
                latestRelease = application.get().getLatestStableVersion();
            }

            if (latestRelease.getPublished().isAfter(optionalRelease.get().getPublished())) {
                response.body(GSON.toJson(new UpdateCheckResponse(true, latestRelease.getVersion(), latestRelease.getChecksum())));
                return HttpStatusCodes.STATUS_CODE_OK;
            }

            response.body(GSON.toJson(new UpdateCheckResponse(false, latestRelease.getVersion(), latestRelease.getChecksum())));
            return HttpStatusCodes.STATUS_CODE_OK;
        })));

        get("/update", (((request, response) -> {
            UpdateCheckPayload updatePayload = GSON.fromJson(request.body(), UpdateCheckPayload.class);
            Optional<Application> application = configuration.getApplicationById(updatePayload.getApplicationId());

            if (application.isEmpty()) {
                response.status(HttpStatusCodes.STATUS_CODE_BAD_REQUEST);
                response.body("Application not found.");
                return HttpStatusCodes.STATUS_CODE_BAD_REQUEST;
            }

            Optional<Release> optionalRelease = application.get().getRelease(updatePayload.getVersion());

            if (optionalRelease.isEmpty()) {
                response.status(HttpStatusCodes.STATUS_CODE_BAD_REQUEST);
                response.body("Invalid release");
                return HttpStatusCodes.STATUS_CODE_BAD_REQUEST;
            }

            File file = new File(optionalRelease.get().getFile());
            if (!file.exists()) {
                response.status(HttpStatusCodes.STATUS_CODE_SERVER_ERROR);
                response.body("File not found.");
                return HttpStatusCodes.STATUS_CODE_SERVER_ERROR;
            }

            response.header("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
            response.header("X-Content-Type-Options", "nosniff");
            response.type("application/octet-stream");

            HttpServletResponse raw = response.raw();
            try (var output = raw.getOutputStream()) {
                Files.copy(file.toPath(), output);
                // output.flush();
            } catch (IOException e) {
                log.error("An error occured while writing the output stream.", e);
                response.status(HttpStatusCodes.STATUS_CODE_SERVER_ERROR);
                response.body("File not found.");
                return HttpStatusCodes.STATUS_CODE_SERVER_ERROR;
            }

            String name = file.getName();

            optionalRelease.get().downloaded();
            response.status(HttpStatusCodes.STATUS_CODE_OK);

            return response.raw();
        })));

        get("/download", ((request, response) -> {
            int id;

            try {
                id = Integer.parseInt(request.queryParams("id"));
            } catch (NumberFormatException e) {
                response.status(HttpStatusCodes.STATUS_CODE_BAD_REQUEST);
                return HttpStatusCodes.STATUS_CODE_BAD_REQUEST;
            }

            Optional<Application> application = configuration.getApplicationById(id);

            if (application.isEmpty()) {
                response.status(HttpStatusCodes.STATUS_CODE_BAD_REQUEST);
                response.body("Application not found.");
                return HttpStatusCodes.STATUS_CODE_BAD_REQUEST;
            }

            Optional<Release> optionalRelease = application.get()
                    .getRelease(request.queryParams("version").replace("_", " "));

            if (optionalRelease.isEmpty()) {
                response.status(HttpStatusCodes.STATUS_CODE_BAD_REQUEST);
                response.body("Invalid release");
                return HttpStatusCodes.STATUS_CODE_BAD_REQUEST;
            }

            File file = new File(optionalRelease.get().getFile());
            if (!file.exists()) {
                response.status(HttpStatusCodes.STATUS_CODE_SERVER_ERROR);
                response.body("File not found.");
                return HttpStatusCodes.STATUS_CODE_SERVER_ERROR;
            }

            response.header("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
            response.header("X-Content-Type-Options", "nosniff");
            response.type("application/octet-stream");

            HttpServletResponse raw = response.raw();
            try (var output = raw.getOutputStream()) {
                Files.copy(file.toPath(), output);
                // output.flush();
            } catch (IOException e) {
                log.error("An error occured while writing the output stream.", e);
                response.status(HttpStatusCodes.STATUS_CODE_SERVER_ERROR);
                response.body("File not found.");
                return HttpStatusCodes.STATUS_CODE_SERVER_ERROR;
            }

            optionalRelease.get().downloaded();
            response.status(HttpStatusCodes.STATUS_CODE_OK);

            return response.raw();
        }));

        post("/webhook/:hash", ((request, response) -> {
            String webhook = request.params(":hash");

            Optional<Application> applicationByWebhook = configuration.getApplicationByWebhook(webhook);
            if (applicationByWebhook.isEmpty()) {
                response.status(HttpStatusCodes.STATUS_CODE_BAD_REQUEST);
                return HttpStatusCodes.STATUS_CODE_BAD_REQUEST;
            }

            response.status(HttpStatusCodes.STATUS_CODE_OK);

            Application application = applicationByWebhook.get();

            GithubReleasePayload payload = GSON.fromJson(request.body(), GithubReleasePayload.class);

            if (!C.isInArray(payload.getAction(), "released", "prereleased")) {
                response.status(HttpStatusCodes.STATUS_CODE_OK);
                return HttpStatusCodes.STATUS_CODE_OK;
            }

            GithubReleasePayload.GitRelease gitRelease = payload.getRelease();

            var split = gitRelease.getTag().split("\\\\");
            String version = split[split.length - 1];
            if (gitRelease.getAssets().isEmpty()) {
                return HttpStatusCodes.STATUS_CODE_OK;
            }

            Optional<File> optFile = FileHelper.getFileFromURL(gitRelease.getAssets().get(0).url);

            if (optFile.isEmpty()) {
                log.error("Failed to download file.");
                return HttpStatusCodes.STATUS_CODE_OK;
            }

            File source = optFile.get();

            boolean exists = source.exists();

            byte[] bytes;
            try {
                bytes = FileUtils.readFileToByteArray(source);
            } catch (IOException e) {
                log.error("Failed to create hash from file.", e);
                return HttpStatusCodes.STATUS_CODE_OK;
            }

            Path resources;
            try {
                resources = Files.createDirectories(Paths.get(FileUtil.home(), "resources",
                        Integer.toString(application.getId()),
                        version));
            } catch (IOException e) {
                log.error("Could not create version file");
                return HttpStatusCodes.STATUS_CODE_OK;
            }

            Matcher matcher = C.FILE_NAME.matcher(source.getName());
            if (!matcher.find()) {
                log.info("Could not parse file name " + source.getName());
                return HttpStatusCodes.STATUS_CODE_OK;
            }


            String hash = Hashing.sha256().hashBytes(bytes).toString();
            Path targetPath = Paths.get(resources.toString(),
                    application.getIdentifier() + "." + matcher.group(2));
            File target = targetPath.toFile();
            try {
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.warn("Failed to save file.", e);
                return HttpStatusCodes.STATUS_CODE_OK;
            }
            String publishedDate = C.DATE_FORMAT.format(LocalDateTime.now().atZone(ZoneId.of("UCT")));

            Release release = new Release(version, gitRelease.getName(), gitRelease.getBody(),
                    gitRelease.isPrerelease(),
                    gitRelease.getCreatedAt().replaceAll("[TZ]", " "), target.toString(), hash);

            application.addRelease(version, release);
            configuration.save();

            response.status(HttpStatusCodes.STATUS_CODE_OK);
            return HttpStatusCodes.STATUS_CODE_OK;
        }));
    }
}

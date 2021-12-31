package de.eldoria.updatebutler.api.updates;

import com.google.api.client.http.HttpStatusCodes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.eldoria.updatebutler.api.RateLimiter;
import de.eldoria.updatebutler.config.Application;
import de.eldoria.updatebutler.config.Configuration;
import de.eldoria.updatebutler.config.Release;
import de.eldoria.updatebutler.config.ReleaseBuilder;
import de.eldoria.updatebutler.util.C;
import io.javalin.Javalin;
import lombok.extern.slf4j.Slf4j;
import spark.Request;
import spark.Response;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static spark.Spark.get;
import static spark.Spark.post;

@Slf4j
public class UpdatesAPI {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private final Configuration configuration;
    private final RateLimiter downloadLimiter = new RateLimiter(5, ChronoUnit.SECONDS);

    public UpdatesAPI(Javalin javalin, Configuration configuration) {
        this.configuration = configuration;

        javalin.get("/check", ctx -> {
            int id;
            try {
                id = Integer.parseInt(ctx.queryParam("id"));
            } catch (NumberFormatException e) {
                ctx.result("Invalid number").status(HttpStatusCodes.STATUS_CODE_BAD_REQUEST);
                return;
            }
            String version = ctx.queryParam("version");
            boolean devBuild = Boolean.parseBoolean(ctx.queryParam("devbuild"));

            Optional<Application> optApplication = configuration.getApplicationById(id);

            if (optApplication.isEmpty()) {
                ctx.json(new UpdateCheckResponse(false, null, null))
                        .status(HttpStatusCodes.STATUS_CODE_BAD_REQUEST);
                return;
            }

            var app = optApplication.get();

            Optional<Release> optionalRelease = optApplication.get().getRelease(version);

            Optional<Release> latestRelease = devBuild ? app.getLatestVersion() : app.getLatestStableVersion();

            if (latestRelease.isEmpty()) {
                ctx.status(HttpStatusCodes.STATUS_CODE_NOT_FOUND).result("This release does not exist");
                return;
            }

            Release release = latestRelease.get();

            if (optionalRelease.isEmpty()) {
                ctx.status(HttpStatusCodes.STATUS_CODE_OK)
                        .json(new UpdateCheckResponse(true, release.getVersion(), release.getChecksum()));
                return;
            }

            ctx.json(new UpdateCheckResponse(release.getPublished().isAfter(optionalRelease.get().getPublished()), release.getVersion(), release.getChecksum()))
                    .status(HttpStatusCodes.STATUS_CODE_OK);
        });
        javalin.get("/download", ctx -> {

        });
        javalin.post("/download", ctx -> {

        });

        javalin.post("/webhook/{hash}/github")

        get("/check", (((request, response) -> {
            int id;
            try {
                id = Integer.parseInt(request.queryParams("id"));
            } catch (NumberFormatException e) {
                response.body("Invalid number");
                return HttpStatusCodes.STATUS_CODE_BAD_REQUEST;
            }
            String version = request.queryParams("version");
            boolean devBuild = Boolean.parseBoolean(request.queryParams("devbuild"));

            Optional<Application> application = configuration.getApplicationById(id);

            if (application.isEmpty()) {
                response.body(GSON.toJson(new UpdateCheckResponse(false, null, null)));
                response.status(HttpStatusCodes.STATUS_CODE_BAD_REQUEST);
                return HttpStatusCodes.STATUS_CODE_BAD_REQUEST;
            }

            Optional<Release> optionalRelease = application.get().getRelease(version);


            Optional<Release> latestRelease;

            if (devBuild) {
                latestRelease = application.get().getLatestVersion();
            } else {
                latestRelease = application.get().getLatestStableVersion();
            }

            if (latestRelease.isEmpty()) {
                response.status(HttpStatusCodes.STATUS_CODE_NOT_FOUND);
                return HttpStatusCodes.STATUS_CODE_NOT_FOUND + " This release does not exist";
            }

            Release release = latestRelease.get();

            if (optionalRelease.isEmpty()) {
                response.status(HttpStatusCodes.STATUS_CODE_OK);
                return GSON.toJson(new UpdateCheckResponse(true, release.getVersion(), release.getChecksum()));
            }

            if (release.getPublished().isAfter(optionalRelease.get().getPublished())) {
                return GSON.toJson(new UpdateCheckResponse(true, release.getVersion(), release.getChecksum()));
            }

            return GSON.toJson(new UpdateCheckResponse(false, release.getVersion(), release.getChecksum()));
        })));

        post("/download", ((request, response) -> {
            response.status(HttpStatusCodes.STATUS_CODE_OK);
            return "<a href=\"" + configuration.getHostName() + "/download?" + request.queryString() + "\">Click here to download.</a>";
        }));

        get("/download", ((request, response) -> {
            downloadLimiter.assertRateLimit(request);
            try {
                return getOutputFileStream(request, response, Integer.parseInt(request.queryParams("id")),
                        request.queryParams("version"));
            } catch (NumberFormatException e) {
                response.status(HttpStatusCodes.STATUS_CODE_BAD_REQUEST);
                return HttpStatusCodes.STATUS_CODE_BAD_REQUEST;
            }
        }));

        post("/webhook/:hash/github", ((request, response) -> {
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

            Optional<Release> buildRelease = ReleaseBuilder.buildRelease(application, version, gitRelease.getName(), gitRelease.getBody(), gitRelease.getAssets().get(0).getUrl(), gitRelease.isPrerelease());
            if (buildRelease.isEmpty()) {
                return HttpStatusCodes.STATUS_CODE_OK;
            }

            configuration.addRelease(application, buildRelease.get());

            response.status(HttpStatusCodes.STATUS_CODE_OK);
            return HttpStatusCodes.STATUS_CODE_OK;
        }));
    }

    private Object getOutputFileStream(Request request, Response response, int id, String version) {
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
        configuration.save();
        log.debug("Delivered release {}", optionalRelease.get().getVersion());
        response.status(HttpStatusCodes.STATUS_CODE_OK);

        return response.raw();
    }
}

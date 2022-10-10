package de.eldoria.updatebutler.api.updates;

import de.eldoria.updatebutler.api.RateLimiter;
import de.eldoria.updatebutler.config.Application;
import de.eldoria.updatebutler.config.Configuration;
import de.eldoria.updatebutler.config.Release;
import de.eldoria.updatebutler.config.ReleaseBuilder;
import de.eldoria.updatebutler.util.C;
import io.javalin.Javalin;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.http.HttpStatus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Slf4j
public class UpdatesAPI {
    private final Configuration configuration;
    private final RateLimiter downloadLimiter = new RateLimiter(5, ChronoUnit.SECONDS);

    public UpdatesAPI(Javalin javalin, Configuration configuration) {
        this.configuration = configuration;

        javalin.get("/check", ctx -> {
            int id;
            try {
                id = Integer.parseInt(ctx.queryParam("id"));
            } catch (NumberFormatException e) {
                ctx.result("Invalid number").status(HttpStatus.BAD_REQUEST_400);
                return;
            }
            String version = ctx.queryParam("version");
            boolean devBuild = Boolean.parseBoolean(ctx.queryParam("devbuild"));

            Optional<Application> optApplication = configuration.getApplicationById(id);

            if (optApplication.isEmpty()) {
                ctx.json(new UpdateCheckResponse(false, null, null))
                        .status(HttpStatus.BAD_REQUEST_400);
                return;
            }

            var app = optApplication.get();

            Optional<Release> optionalRelease = optApplication.get().getRelease(version);

            Optional<Release> latestRelease = devBuild ? app.getLatestVersion() : app.getLatestStableVersion();

            if (latestRelease.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND_404).result("This release does not exist");
                return;
            }

            Release release = latestRelease.get();

            if (optionalRelease.isEmpty()) {
                ctx.status(HttpStatus.OK_200)
                        .json(new UpdateCheckResponse(true, release.version(), release.checksum()));
                return;
            }

            ctx.json(new UpdateCheckResponse(release.published().isAfter(optionalRelease.get().published()), release.version(), release.checksum()))
                    .status(HttpStatus.OK_200);
        });

        javalin.get("/download", ctx -> {
            downloadLimiter.assertRateLimit(ctx);
            try {
                writeOutputStream(ctx, Integer.parseInt(ctx.queryParam("id")),
                        ctx.queryParam("version"));
            } catch (NumberFormatException e) {
                ctx.status(HttpStatus.BAD_REQUEST_400);
            }
        });

        javalin.post("/download", ctx -> {
            ctx.status(HttpStatus.OK_200)
                    .result("<a href=\"" + configuration.getHostName() + "/download?" + ctx.queryString() + "\">Click here to download.</a>");
        });

        javalin.post("/webhook/{hash}/github", ctx -> {
            String webhook = ctx.pathParam("hash");

            Optional<Application> applicationByWebhook = configuration.getApplicationByWebhook(webhook);
            if (applicationByWebhook.isEmpty()) {
                ctx.status(HttpStatus.BAD_REQUEST_400);
                return;
            }

            ctx.status(HttpStatus.OK_200);

            Application application = applicationByWebhook.get();

            GithubReleasePayload payload = ctx.bodyAsClass(GithubReleasePayload.class);

            if (!C.isInArray(payload.getAction(), "released", "prereleased")) {
                ctx.status(HttpStatus.OK_200);
                return;
            }

            GithubReleasePayload.GitRelease gitRelease = payload.getRelease();

            var split = gitRelease.getTag().split("\\\\");
            String version = split[split.length - 1];
            if (gitRelease.getAssets().isEmpty()) return;

            Optional<Release> buildRelease = ReleaseBuilder.buildRelease(application, version, gitRelease.getName(), gitRelease.getBody(), gitRelease.getAssets().get(0).getUrl(), gitRelease.isPrerelease());
            if (buildRelease.isEmpty()) {
                return;
            }

            configuration.addRelease(application, buildRelease.get());
            ctx.status(HttpStatus.OK_200);
        });
    }

    private void writeOutputStream(Context ctx, int id, String version) {
        Optional<Application> application = configuration.getApplicationById(id);

        if (application.isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST_400);
            ctx.result("Application not found.");
            return;
        }

        Optional<Release> optionalRelease = application.get()
                .getRelease(ctx.queryParam("version").replace("_", " "));

        if (optionalRelease.isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST_400).result("Invalid release");
            return;
        }

        File file = new File(optionalRelease.get().file());
        if (!file.exists()) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR_500);
            ctx.result("File not found.");
            return;
        }

        ctx.header("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.contentType("application/octet-stream");


        try {
            ctx.result(new FileInputStream(file)).status(HttpStatus.OK_200);
        } catch (FileNotFoundException e) {
            log.error("File not found.", e);
        }

        optionalRelease.get().downloaded();
        configuration.save();
        log.debug("Delivered release {}", optionalRelease.get().version());
    }
}

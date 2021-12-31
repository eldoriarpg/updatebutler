package de.eldoria.updatebutler.api;

import de.eldoria.updatebutler.api.debug.DebugAPI;
import de.eldoria.updatebutler.api.updates.UpdatesAPI;
import de.eldoria.updatebutler.config.Configuration;
import io.javalin.Javalin;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

public class WebAPI {
    private static final Logger log = getLogger(WebAPI.class);

    private final Javalin javalin;
    private final DebugAPI debugAPI;
    private final UpdatesAPI updatesAPI;
    private final RateLimiter rateLimiter = new RateLimiter(1, ChronoUnit.SECONDS);

    public WebAPI(Configuration configuration, DataSource source) {
        initAPI(configuration);
        javalin = Javalin.create()
                .start(configuration.getHost(), configuration.getPort());

        debugAPI = new DebugAPI(source, javalin, configuration);
        updatesAPI = new UpdatesAPI(javalin, configuration);
    }

    private void initAPI(Configuration configuration) {
        javalin.options("/*", ctx -> {
            String accessControlRequestHeaders = ctx
                    .header("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                ctx.header("Access-Control-Allow-Headers",
                        "Authorization");
            }

            String accessControlRequestMethod = ctx
                    .header("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                ctx.header("Access-Control-Allow-Methods",
                        "HEAD, GET, POST, OPTIONS");
            }
            ctx.status(HttpStatus.OK_200);
        });

        javalin.get("/assets/<file>", ctx -> {
            String params = ctx.pathParam("file");
            if ("tailwind.css".equals(params)) {
                try (BufferedReader inputStream = new BufferedReader(
                        new InputStreamReader(getClass().getClassLoader().getResourceAsStream("tailwind.css")))) {
                    ctx.status(HttpStatus.OK_200)
                            .header("content-type", "text/css")
                            .result(inputStream.lines().collect(Collectors.joining(System.lineSeparator())));
                } catch (IOException e) {
                    log.error("Could not load css stylesheet", e);
                    ctx.status(HttpStatus.NOT_FOUND_404);
                    return;
                }
            }
        });

        javalin.before(ctx -> {
            log.trace("Received request on route: {} {}\nHeaders:\n{}\nBody:\n{}",
                    ctx.method() + " " + ctx.url(),
                    ctx.queryString(),
                    ctx.headerMap().entrySet().stream().map(h -> "   " + h.getKey() + ": " + h.getValue())
                            .collect(Collectors.joining("\n")),
                    ctx.body().substring(0, Math.min(ctx.body().length(), 180)));
            ctx.header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Headers", "*")
                    .header("Content-Security-Policy", "default-src 'self'; script-src 'none'; frame-src 'none'; style-src 'self'; img-src eldoria.de discordapp.com; media-src 'none'");
        });

        javalin.after(ctx -> {
            log.trace("Answered request on route: {} {}\nStatus: {}\nHeaders:\n{}\nBody:\n{}",
                    ctx.method() + " " + ctx.url(),
                    ctx.queryString(),
                    ctx.status(),
                    ctx.res.getHeaderNames().stream().map(h -> "   " + h + ": " + ctx.res.getHeader(h))
                            .collect(Collectors.joining("\n")),
                    ctx.resultString().substring(0, Math.min(ctx.resultString().length(), 180)));
        });
    }
}

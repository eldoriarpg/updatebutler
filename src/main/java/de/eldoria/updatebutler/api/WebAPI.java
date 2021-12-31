package de.eldoria.updatebutler.api;

import com.google.api.client.http.HttpStatusCodes;
import de.eldoria.updatebutler.api.debug.DebugAPI;
import de.eldoria.updatebutler.api.updates.UpdatesAPI;
import de.eldoria.updatebutler.config.Configuration;
import io.javalin.Javalin;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

import static spark.Spark.afterAfter;
import static spark.Spark.before;
import static spark.Spark.get;
import static spark.Spark.halt;
import static spark.Spark.ipAddress;
import static spark.Spark.options;
import static spark.Spark.port;

@Slf4j
public class WebAPI {
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
                        "HEAD, GET, POST, OPTIONS");
            }

            return "OK";
        });

        get("/assets/:file", ((request, response) -> {
            String params = request.params(":file");
            if ("tailwind.css".equals(params)) {
                try (BufferedReader inputStream = new BufferedReader(
                        new InputStreamReader(getClass().getClassLoader().getResourceAsStream("tailwind.css")))) {
                    response.status(HttpStatusCodes.STATUS_CODE_OK);
                    response.header("content-type", "text/css");
                    response.body(inputStream.lines().collect(Collectors.joining(System.lineSeparator())));
                } catch (IOException e) {
                    log.error("Could not load css stylesheet", e);
                    halt(HttpStatusCodes.STATUS_CODE_NOT_FOUND);
                }
            }
            return response.body();
        }));

        before((request, response) -> {
            log.trace("Received request on route: {} {}\nHeaders:\n{}\nBody:\n{}",
                    request.requestMethod() + " " + request.uri(),
                    request.queryString(),
                    request.headers().stream().map(h -> "   " + h + ": " + request.headers(h))
                            .collect(Collectors.joining("\n")),
                    request.body().substring(0, Math.min(request.body().length(), 180)));
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Headers", "*");
            response.header("Content-Security-Policy", "default-src 'self'; script-src 'none'; frame-src 'none'; style-src 'self'; img-src eldoria.de discordapp.com; media-src 'none'");
        });

        afterAfter(((request, response) -> {
            log.trace("Answered request on route: {} {}\nStatus: {}\nHeaders:\n{}\nBody:\n{}",
                    request.requestMethod() + " " + request.uri(),
                    request.queryString(),
                    response.raw().getStatus(),
                    response.raw().getHeaderNames().stream().map(h -> "   " + h + ": " + response.raw().getHeader(h))
                            .collect(Collectors.joining("\n")),
                    response.body().substring(0, Math.min(response.body().length(), 180)));
        }));
    }
}

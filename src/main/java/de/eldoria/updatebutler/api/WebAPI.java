package de.eldoria.updatebutler.api;

import de.eldoria.updatebutler.api.debug.DebugAPI;
import de.eldoria.updatebutler.api.updates.UpdatesAPI;
import de.eldoria.updatebutler.config.Configuration;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

import static spark.Spark.before;
import static spark.Spark.ipAddress;
import static spark.Spark.options;
import static spark.Spark.port;

@Slf4j
public class WebAPI {
    private final DebugAPI debugAPI;
    private final UpdatesAPI updatesAPI;
    private final RateLimiter rateLimiter = new RateLimiter(1, ChronoUnit.SECONDS);

    public WebAPI(Configuration configuration, DataSource source) {
        initAPI(configuration);
        debugAPI = new DebugAPI(source, configuration);
        updatesAPI = new UpdatesAPI(configuration);
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

        before((request, response) -> {
            log.trace("Received request on route: {} {}\nHeaders:\n{}\nBody:\n{}",
                    request.requestMethod() + " " + request.uri(),
                    request.queryString(),
                    request.headers().stream().map(h -> "   " + h + ": " + request.headers(h))
                            .collect(Collectors.joining("\n")),
                    request.body());
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Headers", "*");
        });
    }
}

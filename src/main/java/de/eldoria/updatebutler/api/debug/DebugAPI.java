package de.eldoria.updatebutler.api.debug;

import com.google.api.client.http.HttpStatusCodes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.eldoria.updatebutler.api.RateLimiter;
import de.eldoria.updatebutler.api.debug.data.EntryData;
import de.eldoria.updatebutler.api.debug.data.PluginMetaData;
import de.eldoria.updatebutler.config.Configuration;
import de.eldoria.updatebutler.database.DebugData;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;

import static spark.Spark.get;
import static spark.Spark.path;
import static spark.Spark.post;

@Slf4j
public class DebugAPI {

    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final DebugData debugData;
    private final Configuration configuration;
    private final RateLimiter submitLimiter = new RateLimiter(10, ChronoUnit.SECONDS);

    private final String content =
            "    <section class=\"w-full shadow-sm\">\n" +
                    "        <h3 class=\"text-white text-xl bg-indigo-500 p-2\">{{ contentTitle }}</h3>\n" +
                    "        <pre class=\"whitespace-pre-wrap bg-gray-200 p-2\">{{ content }}</pre>\n" +
                    "    </section>\n";

    private final String pageTemplate;

    public DebugAPI(DataSource source, Configuration configuration) {
        debugData = new DebugData(source);
        this.configuration = configuration;
        init();
        try (BufferedReader inputStream = new BufferedReader(
                new InputStreamReader(getClass().getClassLoader().getResourceAsStream("debugPage.html")))) {
            pageTemplate = inputStream.lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            log.error("Could not load debug page templace", e);
            throw new IllegalStateException("Could not load debug page templace.", e);
        }
    }

    private void init() {
        path("/debug", () -> {
            path("/v1", () -> {
                post("/submit", (request, response) -> {
                    submitLimiter.assertRateLimit(request);
                    DebugPayload debugPayload = gson.fromJson(request.body(), DebugPayload.class);
                    Optional<Pair<Integer, String>> data = debugData.submitDebug(debugPayload);
                    if (data.isPresent()) {
                        response.status(HttpStatusCodes.STATUS_CODE_OK);
                        response.body(data.get().getRight());
                        return data.get().getRight();
                    }
                    response.status(HttpStatusCodes.STATUS_CODE_UNPROCESSABLE_ENTITY);
                    return HttpStatusCodes.STATUS_CODE_UNPROCESSABLE_ENTITY;
                });

                get("/read/:hash", ((request, response) -> {
                    String hash = request.params(":hash");

                    if (hash == null) {
                        response.status(HttpStatusCodes.STATUS_CODE_NOT_FOUND);
                        return HttpStatusCodes.STATUS_CODE_NOT_FOUND;
                    }

                    Optional<DebugPayload> debugPayload = debugData.loadDebug(hash);
                    if (debugPayload.isEmpty()) {
                        response.status(HttpStatusCodes.STATUS_CODE_NOT_FOUND);
                        return HttpStatusCodes.STATUS_CODE_NOT_FOUND;
                    }

                    response.type("text/html");

                    response.body(debugPayload.get().toHtml());

                    response.status(HttpStatusCodes.STATUS_CODE_OK);
                    return response.body();
                }));
            });
        });
    }

}

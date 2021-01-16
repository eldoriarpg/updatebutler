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

import static spark.Spark.before;
import static spark.Spark.get;
import static spark.Spark.path;
import static spark.Spark.post;

@Slf4j
public class DebugAPI {

    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final DebugData debugData;
    private final Configuration configuration;
    private final RateLimiter submitLimiter = new RateLimiter(10, ChronoUnit.SECONDS);

    private static final String CONTENT =
            "    <section class=\"w-full shadow-sm\">\n" +
                    "        <details class=\"cursor-pointer\">\n" +
                    "            <summary class=\"flex items-center bg-eldoria-accent text-white p-2 outline-none select-none\">\n" +
                    "                <h3 class=\"text-xl\">{{ contentTitle }}</h3>\n" +
                    "            </summary>\n" +
                    "\n" +
                    "            <pre class=\"whitespace-pre-wrap bg-eldoria-input p-2\">{{ content }}</pre>\n" +
                    "        </details>\n" +
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
                before();

                post("/submit", (request, response) -> {
                    submitLimiter.assertRateLimit(request);

                    DebugPayload debugPayload = gson.fromJson(request.body(), DebugPayload.class);
                    Optional<DebugResponse> data = debugData.submitDebug(debugPayload);
                    if (data.isPresent()) {
                        response.status(HttpStatusCodes.STATUS_CODE_OK);
                        response.body(gson.toJson(data.get()));
                        return response.body();
                    }
                    response.status(HttpStatusCodes.STATUS_CODE_UNPROCESSABLE_ENTITY);
                    return HttpStatusCodes.STATUS_CODE_UNPROCESSABLE_ENTITY;
                });

                get("/delete/:hash", ((request, response) -> {
                    String hash = request.params(":hash");

                    if (hash == null) {
                        response.status(HttpStatusCodes.STATUS_CODE_NOT_FOUND);
                        return HttpStatusCodes.STATUS_CODE_NOT_FOUND + " Please provide the deletion hash.";
                    }

                    OptionalInt id = debugData.getIdFromDeletionHash(hash);

                    if (id.isEmpty()) {
                        response.status(HttpStatusCodes.STATUS_CODE_NOT_FOUND);
                        return HttpStatusCodes.STATUS_CODE_NOT_FOUND + " Invalid hash.";
                    }

                    debugData.deleteDebug(id.getAsInt());

                    String page = pageTemplate.replace("{{ reportId }}", "none");
                    page = page.replace("{{ title }}", "Report deleted.");
                    page = page.replace("{{ pageUrl }}", configuration.getHostName() + "/debug/v1/delete/" + hash);
                    page = page.replace("{{ favicon }}", "https://eldoria.de/favicon-196x196.png");
                    page = page.replace("{{ content }}", "");

                    response.body(page);
                    response.status(HttpStatusCodes.STATUS_CODE_OK);
                    return response.body();
                }));
                get("/read/:hash", ((request, response) -> {
                    String hash = request.params(":hash");

                    if (hash == null) {
                        response.status(HttpStatusCodes.STATUS_CODE_NOT_FOUND);
                        return HttpStatusCodes.STATUS_CODE_NOT_FOUND + " Please provide the deletion hash.";
                    }

                    OptionalInt id = debugData.getIdFromHash(hash);

                    if (id.isEmpty()) {
                        response.status(HttpStatusCodes.STATUS_CODE_NOT_FOUND);
                        return HttpStatusCodes.STATUS_CODE_NOT_FOUND + " Invalid hash.";
                    }

                    Optional<DebugPayload> debugPayload = debugData.loadDebug(id.getAsInt());
                    if (debugPayload.isEmpty()) {
                        response.status(HttpStatusCodes.STATUS_CODE_NOT_FOUND);
                        return HttpStatusCodes.STATUS_CODE_NOT_FOUND;
                    }

                    response.type("text/html");

                    DebugPayload payload = debugPayload.get();

                    List<String> contents = new ArrayList<>();
                    PluginMetaData pluginMeta = payload.getPluginMeta();
                    contents.add(getContent("Plugin Meta", payload.getPluginMeta()));
                    contents.add(getContent("Server Meta", payload.getServerMeta()));
                    for (EntryData entryData : payload.getAdditionalPluginMeta()) {
                        contents.add(getContent(entryData.getName(), entryData.getContent()));
                    }

                    contents.add(getContent("Latest.log", payload.getLatestLog().getLog()));
                    if (payload.getLatestLog().getInternalExceptions().length != 0) {
                        contents.add(getContent("Internal Exceptions and Warnings",
                                String.join("\n\n", payload.getLatestLog().getInternalExceptions())));
                    }

                    if (payload.getLatestLog().getExceptions().length != 0) {
                        contents.add(getContent("External Exceptions and Warnings",
                                String.join("\n\n", payload.getLatestLog().getExceptions())));
                    }

                    for (EntryData configDump : payload.getConfigDumps()) {
                        contents.add(getContent(configDump.getName(), configDump.getContent()));
                    }

                    String page = pageTemplate.replace("{{ content }}", String.join("\n", contents));
                    page = page.replace("{{ reportId }}", hash);
                    page = page.replace("{{ title }}", "Report created for " + pluginMeta.getName() + " - " + pluginMeta.getVersion());
                    page = page.replace("{{ pageUrl }}", configuration.getHostName() + "/debug/v1/read/" + hash);
                    page = page.replace("{{ favicon }}", "https://eldoria.de/favicon-196x196.png");

                    response.body(page);

                    response.status(HttpStatusCodes.STATUS_CODE_OK);
                    return response.body();
                }));
            });
        });
    }

    private String getContent(String title, Object content) {
        return CONTENT.replace("{{ contentTitle }}", title).replace("{{ content }}", content.toString());
    }

}

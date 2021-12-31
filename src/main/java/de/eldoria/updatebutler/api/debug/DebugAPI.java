package de.eldoria.updatebutler.api.debug;

import de.eldoria.updatebutler.api.RateLimiter;
import de.eldoria.updatebutler.api.debug.data.EntryData;
import de.eldoria.updatebutler.api.debug.data.PluginMetaData;
import de.eldoria.updatebutler.config.Configuration;
import de.eldoria.updatebutler.database.DebugData;
import io.javalin.Javalin;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static org.slf4j.LoggerFactory.getLogger;

public class DebugAPI {
    private static final Logger log = getLogger(DebugAPI.class);
    private Javalin debugApi;
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
            "            <pre class=\"whitespace-pre-wrap bg-eldoria-input break-all sm:break-normal p-2\">{{ content }}</pre>\n" +
            "        </details>\n" +
            "    </section>\n";
    private final String pageTemplate;

    public DebugAPI(DataSource source, Javalin javalin, Configuration configuration) {
        debugApi = javalin;
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
        debugApi.routes(() -> {
            path("debug", () -> {
                path("v1", () -> {
                    get("submit", ctx -> {
                        submitLimiter.assertRateLimit(ctx);

                        DebugPayload debugPayload = ctx.bodyAsClass(DebugPayload.class);
                        Optional<DebugResponse> data = debugData.submitDebug(debugPayload);
                        if (data.isPresent()) {
                            ctx.status(HttpStatus.OK_200).json(data.get());
                            return;
                        }
                        ctx.status(HttpStatus.UNPROCESSABLE_ENTITY_422);
                    });
                    // Todo change page to return a page with a button to send post request.
                    get("delete/{hash}", ctx -> {
                        String hash = ctx.pathParam("hash");

                        if (hash == null) {
                            ctx.status(HttpStatus.NOT_FOUND_404).result("Please provide the deletion hash.");
                            return;
                        }

                        var id = debugData.getIdFromDeletionHash(hash);

                        if (id.isEmpty()) {
                            ctx.status(HttpStatus.NOT_FOUND_404).result("Invalid hash.");
                            return;
                        }

                        debugData.deleteDebug(id.get());

                        var page = pageTemplate.replace("{{ reportId }}", "none");
                        page = page.replace("{{ title }}", "Report deleted.");
                        page = page.replace("{{ pageUrl }}", configuration.getHostName() + "/debug/v1/delete/" + hash);
                        page = page.replace("{{ favicon }}", "https://eldoria.de/favicon-196x196.png");
                        page = page.replace("{{ content }}", "");

                        ctx.result(page).status(HttpStatus.OK_200);
                    });
                    get("read/{hash}", ctx -> {
                        String hash = ctx.pathParam("hash");

                        if (hash == null) {
                            ctx.status(HttpStatus.NOT_FOUND_404).result("Please provide the deletion hash.");
                            return;
                        }

                        var id = debugData.getIdFromHash(hash);

                        if (id.isEmpty()) {
                            ctx.status(HttpStatus.NOT_FOUND_404).result("Invalid hash.");
                            return;
                        }

                        var debugPayload = debugData.loadDebug(id.get());
                        if (debugPayload.isEmpty()) {
                            ctx.status(HttpStatus.NOT_FOUND_404);
                            return;
                        }

                        ctx.contentType("text/html");

                        var payload = debugPayload.get();

                        List<String> contents = new ArrayList<>();
                        PluginMetaData pluginMeta = payload.pluginMeta();
                        contents.add(getContent("Plugin Meta", payload.pluginMeta()));
                        contents.add(getContent("Server Meta", payload.serverMeta()));
                        for (EntryData entryData : payload.additionalPluginMeta()) {
                            contents.add(getContent(entryData.getName(), entryData.getContent()));
                        }

                        contents.add(getContent("Latest.log", payload.latestLog().getLog()));
                        contents.add(getContent("Plugin Log", payload.latestLog().getPluginLog()));

                        if (payload.latestLog().getInternalExceptions().length != 0) {
                            contents.add(getContent("Internal Exceptions and Warnings",
                                    String.join("\n\n", payload.latestLog().getInternalExceptions())));
                        }

                        if (payload.latestLog().getExternalExceptions().length != 0) {
                            contents.add(getContent("External Exceptions and Warnings",
                                    String.join("\n\n", payload.latestLog().getExternalExceptions())));
                        }

                        for (EntryData configDump : payload.configDumps()) {
                            contents.add(getContent(configDump.getName(), configDump.getContent()));
                        }

                        String page = pageTemplate.replace("{{ content }}", String.join("\n", contents));
                        page = page.replace("{{ reportId }}", hash);
                        page = page.replace("{{ title }}", "Report created for " + pluginMeta.getName() + " - " + pluginMeta.getVersion());
                        page = page.replace("{{ pageUrl }}", configuration.getHostName() + "/debug/v1/read/" + hash);
                        page = page.replace("{{ favicon }}", "https://eldoria.de/favicon-196x196.png");

                        ctx.result(page).contentType("text/html").status(HttpStatus.OK_200);
                    });
                });
            });
        });
    }

    private String getContent(String title, Object content) {
        return CONTENT.replace("{{ contentTitle }}", title).replace("{{ content }}", content.toString());
    }

}

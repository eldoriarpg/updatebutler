package de.eldoria.updatebutler.api.debug;

import com.google.api.client.http.HttpStatusCodes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.eldoria.updatebutler.api.RateLimiter;
import de.eldoria.updatebutler.api.debug.data.EntryData;
import de.eldoria.updatebutler.api.debug.data.PluginMetaData;
import de.eldoria.updatebutler.config.Configuration;
import de.eldoria.updatebutler.database.DebugData;
import io.javalin.Javalin;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class DebugAPI {

    private final Gson gson = new GsonBuilder().serializeNulls().create();
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
        debugApi.get("/debug/v1/submit", ctx -> {
            submitLimiter.assertRateLimit(ctx);

            DebugPayload debugPayload = gson.fromJson(ctx.body(), DebugPayload.class);
            Optional<DebugResponse> data = debugData.submitDebug(debugPayload);
            if (data.isPresent()) {
                ctx.status(HttpStatusCodes.STATUS_CODE_OK).json(data.get());
                return;
            }
            ctx.status(HttpStatusCodes.STATUS_CODE_UNPROCESSABLE_ENTITY);
            return;
        });

        // Todo change page to return a page with a button to send post request.
        debugApi.get("/debug/v1/delete/{hash}", ctx -> {
            String hash = ctx.pathParam("hash");

            if (hash == null) {
                ctx.status(HttpStatusCodes.STATUS_CODE_NOT_FOUND).result("Please provide the deletion hash.");
                return;
            }

            var id = debugData.getIdFromDeletionHash(hash);

            if (id.isEmpty()) {
                ctx.status(HttpStatusCodes.STATUS_CODE_NOT_FOUND).result("Invalid hash.");
                return;
            }

            debugData.deleteDebug(id.get());

            var page = pageTemplate.replace("{{ reportId }}", "none");
            page = page.replace("{{ title }}", "Report deleted.");
            page = page.replace("{{ pageUrl }}", configuration.getHostName() + "/debug/v1/delete/" + hash);
            page = page.replace("{{ favicon }}", "https://eldoria.de/favicon-196x196.png");
            page = page.replace("{{ content }}", "");

            ctx.result(page).status(HttpStatusCodes.STATUS_CODE_OK);
        });


        debugApi.get("/debug/v1/read/{hash}", ctx -> {
            String hash = ctx.pathParam("hash");

            if (hash == null) {
                ctx.status(HttpStatusCodes.STATUS_CODE_NOT_FOUND).result("Please provide the deletion hash.");
                return;
            }

            var id = debugData.getIdFromHash(hash);

            if (id.isEmpty()) {
                ctx.status(HttpStatusCodes.STATUS_CODE_NOT_FOUND).result("Invalid hash.");
                return;
            }

            var debugPayload = debugData.loadDebug(id.get());
            if (debugPayload.isEmpty()) {
                ctx.status(HttpStatusCodes.STATUS_CODE_NOT_FOUND);
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

            ctx.result(page).contentType("text/html").status(HttpStatusCodes.STATUS_CODE_OK);
        });
    }

    private String getContent(String title, Object content) {
        return CONTENT.replace("{{ contentTitle }}", title).replace("{{ content }}", content.toString());
    }

}

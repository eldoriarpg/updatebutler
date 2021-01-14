package de.eldoria.updatebutler.api.debug;

import com.google.api.client.http.HttpStatusCodes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.eldoria.updatebutler.api.RateLimiter;
import de.eldoria.updatebutler.database.DebugData;
import org.apache.commons.lang3.tuple.Pair;

import javax.sql.DataSource;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static spark.Spark.get;
import static spark.Spark.path;
import static spark.Spark.post;

public class DebugAPI {

    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final DebugData debugData;
    private final RateLimiter submitLimiter = new RateLimiter(10, ChronoUnit.SECONDS);

    public DebugAPI(DataSource source) {
        debugData = new DebugData(source);
        init();
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

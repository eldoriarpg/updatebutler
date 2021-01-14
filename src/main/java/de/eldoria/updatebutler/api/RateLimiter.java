package de.eldoria.updatebutler.api;

import com.google.api.client.http.HttpStatusCodes;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import spark.Request;

import java.time.Instant;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static spark.Spark.halt;

@Slf4j
public class RateLimiter {

    private final Cache<String, Instant> rateLimit = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    private final long time;
    private final TemporalUnit timeUnit;

    public RateLimiter(long time, TemporalUnit timeUnit) {
        this.time = time;
        this.timeUnit = timeUnit;
    }

    public void assertRateLimit(Request request) throws ExecutionException {
        String ip = request.headers("X-Real-IP");
        Instant lastAccess = rateLimit.get(ip, () -> Instant.now().minus(time + 1, timeUnit));
        if (lastAccess.isAfter(Instant.now().minus(time, timeUnit))) {
            log.trace("Rate limited. Request denied.");
            halt(HttpStatusCodes.STATUS_CODE_BAD_REQUEST, "You are rate limited. Please wait.");
        }
        rateLimit.put(ip, Instant.now());
    }
}

package de.eldoria.updatebutler.api;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.javalin.http.Context;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

public class RateLimiter {

    private static final Logger log = getLogger(RateLimiter.class);
    private final Cache<String, Instant> rateLimit = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    private final long time;
    private final TemporalUnit timeUnit;

    public RateLimiter(long time, TemporalUnit timeUnit) {
        this.time = time;
        this.timeUnit = timeUnit;
    }

    public void assertRateLimit(Context ctx) throws ExecutionException {
        String ip = ctx.header("X-Real-IP");
        Instant lastAccess = rateLimit.get(ip, () -> Instant.now().minus(time + 1, timeUnit));
        if (lastAccess.isAfter(Instant.now().minus(time, timeUnit))) {
            log.trace("Rate limited. Request denied.");
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE_503);
            throw new RateLimitException();
        }
        rateLimit.put(ip, Instant.now());
    }

    private static class RateLimitException extends RuntimeException {
        public RateLimitException() {
            super("You are rate limited. Please wait.");
        }
    }
}

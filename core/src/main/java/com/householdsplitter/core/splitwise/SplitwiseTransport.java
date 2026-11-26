package com.householdsplitter.core.splitwise;

import java.io.IOException;
import java.util.Map;

/**
 * The one thing the client needs from the platform: send a request, get a status and body.
 *
 * <p>It is an interface so that this module stays free of Android and so tests can answer
 * with canned JSON instead of reaching the real service.
 */
public interface SplitwiseTransport {

    /** A raw reply. Splitwise reports some failures inside a 200, so the body matters. */
    final class Reply {
        public final int status;
        public final String body;

        public Reply(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    Reply get(String path) throws IOException;

    /** Sends {@code form} as application/x-www-form-urlencoded. */
    Reply post(String path, Map<String, String> form) throws IOException;
}

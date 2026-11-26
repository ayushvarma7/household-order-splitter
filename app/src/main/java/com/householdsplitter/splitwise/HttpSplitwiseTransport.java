package com.householdsplitter.splitwise;

import com.householdsplitter.core.splitwise.SplitwiseTransport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Plain HttpURLConnection, because one small form-encoded API does not justify adding an
 * HTTP library to an app that has none. The key is sent as a bearer token, the way
 * Splitwise accepts a personal API key.
 */
final class HttpSplitwiseTransport implements SplitwiseTransport {

    private static final String BASE = "https://secure.splitwise.com/api/v3.0/";
    private static final int TIMEOUT_MS = 20_000;

    private final KeySource key;

    interface KeySource {
        String apiKey();
    }

    HttpSplitwiseTransport(KeySource key) {
        this.key = key;
    }

    @Override
    public Reply get(String path) throws IOException {
        return call("GET", path, null);
    }

    @Override
    public Reply post(String path, Map<String, String> form) throws IOException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(URLEncoder.encode(entry.getKey(), "UTF-8")).append('=')
                    .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return call("POST", path, body.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Reply call(String method, String path, byte[] body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE + path).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Authorization", "Bearer " + key.apiKey());
            connection.setRequestProperty("Accept", "application/json");
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type",
                        "application/x-www-form-urlencoded; charset=UTF-8");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body);
                }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream()
                    : connection.getInputStream();
            return new Reply(status, stream == null ? "" : read(stream));
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = stream.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return out.toString("UTF-8");
        }
    }
}

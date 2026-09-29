package legalops;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InfraiClient {
    // Canonical calls: infrai.metrics.query, infrai.realtime.channel.create,
    // infrai.realtime.publish, infrai.realtime.token.issue
    private final InfraiConfig config;
    private final HttpClient http;

    public InfraiClient(InfraiConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build());
    }

    InfraiClient(InfraiConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    public Json.Raw queryMetrics() {
        return send("GET", "/v1/metrics/query?name=legal.operations&agg=sum", null, null);
    }

    public Json.Raw createChannel(String channel) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", channel);
        body.put("type", "private");
        body.put("vendor", "infrai");
        return send("POST", "/v1/realtime/channel/create", Json.object(body), UUID.randomUUID().toString());
    }

    public Json.Raw publish(String channel, String event, Json.Raw data, String operationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", channel);
        body.put("event", event);
        body.put("data", data);
        body.put("account_id", "legal-operations");
        return send("POST", "/v1/realtime/publish", Json.object(body), operationId);
    }

    public Json.Raw issueDashboardToken(String clientId, String channel) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_id", clientId);
        body.put("channels", List.of(channel));
        body.put("capabilities", List.of("subscribe"));
        body.put("ttl_seconds", 900);
        return send("POST", "/v1/realtime/token/issue", Json.object(body), UUID.randomUUID().toString());
    }

    private Json.Raw send(String method, String path, String body, String idempotencyKey) {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder request = HttpRequest.newBuilder(resolve(path))
                    .timeout(config.requestTimeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json");
            if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
            if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
            else request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));

            HttpResponse<String> response;
            try {
                response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                throw new IllegalStateException("Infrai transport error", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while calling Infrai", e);
            }

            Envelope envelope = Envelope.decode(response.body());
            if (response.statusCode() == 429 && attempt < 3) {
                pause(retryDelay(response, attempt));
                continue;
            }
            if (!envelope.ok()) {
                throw new InfraiException(envelope.errorCode(), envelope.errorMessage(), response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new IllegalStateException("Infrai transport status " + response.statusCode());
            }
            return new Json.Raw(envelope.data());
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    private URI resolve(String path) {
        return URI.create(config.baseUrl().toString().replaceAll("/$", "") + path);
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> Duration.ofSeconds(Long.parseLong(value)))
                .orElse(Duration.ofMillis(250L << attempt));
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during rate-limit backoff", e);
        }
    }

    private record Envelope(boolean ok, String data, String errorCode, String errorMessage) {
        static Envelope decode(String json) {
            boolean ok = Boolean.parseBoolean(scalar(json, "ok"));
            String data = rawValue(json, "data");
            String error = rawValue(json, "error");
            return new Envelope(ok, data, text(error, "code"), text(error, "message"));
        }

        private static String scalar(String json, String key) {
            return rawValue(json, key).replace("\"", "").trim();
        }

        private static String text(String json, String key) {
            String value = rawValue(json, key).trim();
            return value.length() >= 2 && value.startsWith("\"")
                    ? value.substring(1, value.length() - 1) : value;
        }

        private static String rawValue(String json, String key) {
            String marker = "\"" + key + "\"";
            int keyAt = json.indexOf(marker);
            if (keyAt < 0) return "null";
            int start = json.indexOf(':', keyAt + marker.length()) + 1;
            while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
            if (start >= json.length()) return "null";
            char first = json.charAt(start);
            if (first == '{' || first == '[') {
                char close = first == '{' ? '}' : ']';
                int depth = 0;
                boolean quoted = false;
                boolean escaped = false;
                for (int i = start; i < json.length(); i++) {
                    char c = json.charAt(i);
                    if (quoted) {
                        if (escaped) escaped = false;
                        else if (c == '\\') escaped = true;
                        else if (c == '\"') quoted = false;
                    } else if (c == '\"') quoted = true;
                    else if (c == first) depth++;
                    else if (c == close && --depth == 0) return json.substring(start, i + 1);
                }
            }
            if (first == '\"') {
                boolean escaped = false;
                for (int i = start + 1; i < json.length(); i++) {
                    char c = json.charAt(i);
                    if (escaped) escaped = false;
                    else if (c == '\\') escaped = true;
                    else if (c == '\"') return json.substring(start, i + 1);
                }
            }
            int end = start;
            while (end < json.length() && ",}".indexOf(json.charAt(end)) < 0) end++;
            return json.substring(start, end).trim();
        }
    }
}

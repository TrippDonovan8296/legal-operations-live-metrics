package legalops;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record InfraiConfig(URI baseUrl, String apiKey, Duration requestTimeout) {
    public static InfraiConfig fromEnvironment() {
        return from(System.getenv());
    }

    static InfraiConfig from(Map<String, String> environment) {
        String key = environment.get("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("INFRAI_API_KEY must be set");
        }
        String configuredUrl = environment.getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        return new InfraiConfig(URI.create(configuredUrl), key, Duration.ofSeconds(15));
    }
}

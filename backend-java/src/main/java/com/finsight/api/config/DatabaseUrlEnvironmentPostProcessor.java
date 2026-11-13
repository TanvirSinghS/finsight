package com.finsight.api.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Lets the app run against a single {@code DATABASE_URL} (the form Neon, Render and
 * Heroku hand out) without losing the split {@code DB_URL}/{@code DB_USERNAME}/
 * {@code DB_PASSWORD} variables used for local setups.
 *
 * <p>{@code DB_URL} always wins when present. Otherwise {@code DATABASE_URL} is parsed
 * into the {@code spring.datasource.*} properties Spring Boot reads.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String SOURCE_NAME = "neonDatabaseUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String raw = firstNonBlank(
                environment.getProperty("DATABASE_URL"),
                environment.getProperty("SPRING_DATASOURCE_URL"));
        if (raw == null || hasText(environment.getProperty("DB_URL"))) {
            return;
        }

        try {
            Map<String, Object> properties = parse(raw);
            environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, properties));
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Could not parse DATABASE_URL: " + ex.getMessage(), ex);
        }
    }

    private Map<String, Object> parse(String raw) {
        URI uri = URI.create(raw.trim());
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.startsWith("postgres") || scheme.equals("postgresql"))) {
            throw new IllegalArgumentException("expected a postgres:// or postgresql:// URL but got scheme " + scheme);
        }

        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("URL is missing a host");
        }
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String database = uri.getPath() == null || uri.getPath().isBlank()
                ? "postgres"
                : uri.getPath().replaceFirst("^/", "");

        String userInfo = uri.getUserInfo();
        String username = null;
        String password = null;
        if (userInfo != null && !userInfo.isBlank()) {
            int colon = userInfo.indexOf(':');
            if (colon < 0) {
                username = decode(userInfo);
            } else {
                username = decode(userInfo.substring(0, colon));
                password = decode(userInfo.substring(colon + 1));
            }
        }

        // Neon enables SSL and channel binding; the JDBC driver takes sslmode plus its own
        // 'channelBinding' name, so only the sslmode hint is carried over.
        StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
                .append(host).append(':').append(port).append('/').append(database);
        Map<String, String> query = parseQuery(uri.getRawQuery());
        String sslMode = query.get("sslmode");
        String channelBinding = query.get("channel_binding");
        if (sslMode != null || channelBinding != null) {
            jdbcUrl.append('?');
        }
        if (sslMode != null) {
            jdbcUrl.append("sslmode=").append(sslMode);
        }
        if (channelBinding != null) {
            if (sslMode != null) {
                jdbcUrl.append('&');
            }
            jdbcUrl.append("channelBinding=").append(channelBinding);
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", jdbcUrl.toString());
        if (username != null) {
            properties.put("spring.datasource.username", username);
        }
        if (password != null) {
            properties.put("spring.datasource.password", password);
        }
        return properties;
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return values;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int equals = pair.indexOf('=');
            if (equals < 0) {
                values.put(decode(pair), "");
            } else {
                values.put(decode(pair.substring(0, equals)), decode(pair.substring(equals + 1)));
            }
        }
        return values;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public int getOrder() {
        // Run after config files load so DB_URL overrides are visible.
        return Ordered.LOWEST_PRECEDENCE;
    }
}

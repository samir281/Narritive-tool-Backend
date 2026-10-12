package com.narrativenews.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

/** Everything configurable lives under "app." in application.yml / environment variables. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String baseUrl,
        List<String> corsOrigins,
        Jwt jwt,
        Bootstrap bootstrap,
        Render render,
        Ocr ocr) {

    public record Jwt(String secret, long adminTtlMinutes, long readerTtlMinutes) {}
    public record Bootstrap(String adminEmail, String adminPassword) {}
    public record Render(int thumbWidth, int smallWidth, int largeWidth, float jpegQuality, int maxPages) {}
    public record Ocr(boolean enabled, String datapath, String languages) {}
}

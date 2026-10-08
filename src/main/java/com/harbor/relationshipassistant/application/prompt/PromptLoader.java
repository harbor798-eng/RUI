package com.harbor.relationshipassistant.application.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Minimal classpath loader for Markdown Prompt Contracts.
 * Reads resources under src/main/resources/prompts/. No caching needed for
 * development; calls are cheap and happen once per request.
 */
final class PromptLoader {
    private PromptLoader() {}

    static String load(String classpathPath) {
        try (InputStream in = PromptLoader.class.getResourceAsStream(classpathPath)) {
            if (in == null) {
                throw new IllegalStateException("Prompt resource not found on classpath: " + classpathPath);
            }
            byte[] bytes = in.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load prompt resource: " + classpathPath, e);
        }
    }
}

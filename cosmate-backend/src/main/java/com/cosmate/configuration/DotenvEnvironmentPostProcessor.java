package com.cosmate.configuration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * EnvironmentPostProcessor that loads key=value pairs from a local .env file
 * located at the project working directory (System.getProperty("user.dir")).
 * Lines starting with # or blank lines are ignored. Values wrapped in
 * single or double quotes will have the quotes removed.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "dotenvProperties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path envPath = Paths.get(System.getProperty("user.dir"), ".env");
        if (!Files.exists(envPath)) {
            return; // nothing to do
        }

        Map<String, Object> map = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(envPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int idx = line.indexOf('=');
                if (idx <= 0) continue;
                String key = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                    if (value.length() >= 2) {
                        value = value.substring(1, value.length() - 1);
                    }
                }
                map.put(key, value);
            }
        } catch (IOException e) {
            System.err.println("Could not read .env file: " + e.getMessage());
        }

        if (!map.isEmpty()) {
            MapPropertySource ps = new MapPropertySource(PROPERTY_SOURCE_NAME, map);
            environment.getPropertySources().addFirst(ps);
        }
    }
}


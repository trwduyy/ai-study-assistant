package com.aistudy.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@SpringBootApplication
public class AiStudyAssistantApplication {

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(AiStudyAssistantApplication.class, args);
    }

    private static void loadDotEnv() {
        Path[] possiblePaths = new Path[]{
                Paths.get(".env"),
                Paths.get("..", ".env"),
                Paths.get("../.env")
        };

        for (Path path : possiblePaths) {
            if (Files.exists(path) && Files.isRegularFile(path)) {
                try {
                    List<String> lines = Files.readAllLines(path);
                    for (String line : lines) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        int eqIndex = line.indexOf('=');
                        if (eqIndex > 0) {
                            String key = line.substring(0, eqIndex).trim();
                            String value = line.substring(eqIndex + 1).trim();
                            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                                (value.startsWith("'") && value.endsWith("'"))) {
                                value = value.substring(1, value.length() - 1);
                            }
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, value);
                            }
                            // Also map GEMINI_API_KEY directly to spring property
                            if ("GEMINI_API_KEY".equals(key)) {
                                System.setProperty("app.gemini.api-key", value);
                            }
                            if ("GEMINI_MODEL".equals(key)) {
                                System.setProperty("app.gemini.model", value);
                            }
                        }
                    }
                    System.out.println("Loaded environment variables from: " + path.toAbsolutePath().normalize());
                    break;
                } catch (IOException e) {
                    System.err.println("Failed to read .env file from " + path + ": " + e.getMessage());
                }
            }
        }
    }
}


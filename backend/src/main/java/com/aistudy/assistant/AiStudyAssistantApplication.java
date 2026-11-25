package com.aistudy.assistant;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.TimeZone;

@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
public class AiStudyAssistantApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        loadDotEnv();
        SpringApplication.run(AiStudyAssistantApplication.class, args);
    }

    @PostConstruct
    public void ensureUtcTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
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

package net.atos.mev.calendar.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import net.atos.mev.calendar.config.CalendarCompetitionsProperties;
import net.atos.mev.calendar.service.dto.CompetitionRuntimeOverridesDTO;

@Service
public class CompetitionPropertiesService {

    private static final Logger log = LoggerFactory.getLogger(CompetitionPropertiesService.class);

    private final CalendarCompetitionsProperties calendarCompetitionsProperties;
    private final ResourceLoader resourceLoader;
    private final Path competitionsDirectory;

    public CompetitionPropertiesService(
        CalendarCompetitionsProperties calendarCompetitionsProperties,
        ResourceLoader resourceLoader,
        @Value("${calendar.config.competitions-dir:/config/competitions}") String competitionsDirectory
    ) {
        this.calendarCompetitionsProperties = calendarCompetitionsProperties;
        this.resourceLoader = resourceLoader;
        this.competitionsDirectory = Path.of(competitionsDirectory).toAbsolutePath().normalize();
    }

    public Properties loadFinalProperties(String competitionId) {
        return loadFinalProperties(competitionId, CompetitionRuntimeOverridesDTO.empty());
    }

    public Properties loadFinalProperties(String competitionId, CompetitionRuntimeOverridesDTO runtimeOverrides) {
        var config = findCompetitionConfig(competitionId);
        Properties finalProperties = loadBaseProperties(config.getFile());

        applyIfPresent(finalProperties, "env", config.getEnv());
        applyIfPresent(finalProperties, "start", config.getStart());
        applyIfPresent(finalProperties, "end", config.getEnd());

        if (config.getOverrides() != null) {
            for (Map.Entry<String, String> override : config.getOverrides().entrySet()) {
                applyIfPresent(finalProperties, override.getKey(), override.getValue());
            }
        }

        CompetitionRuntimeOverridesDTO safeRuntimeOverrides = runtimeOverrides == null
            ? CompetitionRuntimeOverridesDTO.empty()
            : runtimeOverrides;

        applyIfPresent(finalProperties, "datesFile", safeRuntimeOverrides.datesFile());
        applyIfPresent(finalProperties, "resultsFolder", safeRuntimeOverrides.resultsFolder());
        if (safeRuntimeOverrides.lastRoundToAssign() != null) {
            finalProperties.setProperty(
                "ScheduleMoroccoAlg.lastRoundToAssign",
                String.valueOf(safeRuntimeOverrides.lastRoundToAssign())
            );
        }

        return finalProperties;
    }

    private CalendarCompetitionsProperties.CompetitionConfig findCompetitionConfig(String competitionId) {
        for (var entry : calendarCompetitionsProperties.getCompetitions().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(competitionId)) {
                if (!StringUtils.hasText(entry.getValue().getFile())) {
                    throw new IllegalArgumentException("Missing file for competitionId: " + competitionId);
                }
                return entry.getValue();
            }
        }
        throw new IllegalArgumentException("Unknown competitionId: " + competitionId);
    }

    private Properties loadBaseProperties(String filePath) {
        Properties properties = new Properties();
        Path filesystemPath = resolveFilesystemPath(filePath);

        if (filesystemPath != null && Files.exists(filesystemPath)) {
            try (InputStream inputStream = Files.newInputStream(filesystemPath);
                 InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                properties.load(reader);
                log.info("Loaded competition properties from filesystem: {}", filesystemPath);
                return properties;
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot load competition properties from filesystem: " + filesystemPath, exception);
            }
        }

        Resource classpathResource = resourceLoader.getResource("classpath:" + filePath);
        if (classpathResource.exists()) {
            try (InputStream inputStream = classpathResource.getInputStream();
                 InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                properties.load(reader);
                log.info("Loaded competition properties from classpath: {}", filePath);
                return properties;
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot load competition properties from classpath: " + filePath, exception);
            }
        }

        String fsLocation = filesystemPath == null ? "N/A" : filesystemPath.toString();
        throw new IllegalStateException(
            "Competition properties not found. file=" + filePath + ", filesystemPath=" + fsLocation + ", classpath=" + filePath
        );
    }

    private Path resolveFilesystemPath(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return null;
        }

        Path configuredPath = Path.of(filePath);
        if (configuredPath.isAbsolute()) {
            return configuredPath.normalize();
        }

        Path resolved = competitionsDirectory.resolve(filePath).normalize();
        if (!resolved.startsWith(competitionsDirectory)) {
            throw new IllegalArgumentException("Invalid competition properties path: " + filePath);
        }
        return resolved;
    }

    private void applyIfPresent(Properties properties, String key, String value) {
        if (StringUtils.hasText(value)) {
            properties.setProperty(key, value);
        }
    }
}

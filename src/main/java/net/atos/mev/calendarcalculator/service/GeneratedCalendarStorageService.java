package net.atos.mev.calendarcalculator.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import net.atos.mev.calendarcalculator.config.CalendarCompetitionsProperties;
import net.atos.mev.calendarcalculator.service.dto.GeneratedCalendarFileDTO;

@Service
public class GeneratedCalendarStorageService {

    private static final String COMPETITION_ID_REGEX = "^[A-Za-z0-9_-]+$";
    private static final String SEASON_REGEX = "^[A-Za-z0-9][A-Za-z0-9._-]*$";
    private static final Pattern VERSION_PATTERN = Pattern.compile(".*-v(\\d+)$", Pattern.CASE_INSENSITIVE);

    private final Path resultsBaseDirectory;
    private final CalendarCompetitionsProperties calendarCompetitionsProperties;

    public GeneratedCalendarStorageService(
        @Value("${calendar.results.base-dir:./results}") String resultsBaseDirectory,
        CalendarCompetitionsProperties calendarCompetitionsProperties
    ) {
        this.resultsBaseDirectory = Path.of(resultsBaseDirectory).toAbsolutePath().normalize();
        this.calendarCompetitionsProperties = calendarCompetitionsProperties;
    }

    public List<GeneratedCalendarFileDTO> listGeneratedFullCalendars(String competitionId, String season) {
        Path seasonDirectory = resolveSeasonDirectory(competitionId, season);
        if (!Files.exists(seasonDirectory)) {
            return List.of();
        }

        String storageDirectory = resolveStorageDirectoryName(competitionId);
        try (Stream<Path> files = Files.walk(seasonDirectory, 6)) {
            return files
                .filter(Files::isRegularFile)
                .filter(this::isFullCalendarFile)
                .filter(file -> belongsToCompetition(file, seasonDirectory, storageDirectory))
                .map(file -> toGeneratedCalendarFileDTO(file, seasonDirectory))
                .sorted(Comparator.comparing(GeneratedCalendarFileDTO::lastModified, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(GeneratedCalendarFileDTO::fileName))
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Cannot list generated full calendars for competitionId " + competitionId + " and season " + season,
                exception
            );
        }
    }

    public Optional<byte[]> readGeneratedFullCalendar(String competitionId, String season, String downloadId) {
        Path seasonDirectory = resolveSeasonDirectory(competitionId, season);
        String storageDirectory = resolveStorageDirectoryName(competitionId);
        Path file = decodeDownloadPath(downloadId, seasonDirectory);

        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        if (!isFullCalendarFile(file)) {
            return Optional.empty();
        }
        if (!belongsToCompetition(file, seasonDirectory, storageDirectory)) {
            return Optional.empty();
        }

        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read generated calendar file: " + file.getFileName(), exception);
        }
    }

    private Path resolveSeasonDirectory(String competitionId, String season) {
        if (competitionId == null || !competitionId.matches(COMPETITION_ID_REGEX)) {
            throw new IllegalArgumentException("Invalid competitionId: " + competitionId);
        }
        if (season == null || !season.matches(SEASON_REGEX)) {
            throw new IllegalArgumentException("Invalid season: " + season);
        }

        Path resolved = resultsBaseDirectory.resolve(season).normalize();
        if (!resolved.startsWith(resultsBaseDirectory)) {
            throw new IllegalArgumentException("Invalid season path");
        }
        return resolved;
    }

    private boolean isFullCalendarFile(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.startsWith("fullCalendar") && fileName.toLowerCase().endsWith(".xlsx");
    }

    private boolean belongsToCompetition(Path file, Path seasonDirectory, String storageDirectory) {
        Path relative = seasonDirectory.relativize(file);
        for (Path segment : relative) {
            String segmentName = segment.toString();
            if (segmentName.equals(storageDirectory) || segmentName.startsWith(storageDirectory + "-")) {
                return true;
            }
        }
        return false;
    }

    private GeneratedCalendarFileDTO toGeneratedCalendarFileDTO(Path file, Path seasonDirectory) {
        Path relative = seasonDirectory.relativize(file);
        String normalizedRelative = relative.toString().replace('\\', '/');
        String downloadId = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(normalizedRelative.getBytes(StandardCharsets.UTF_8));

        String folder = relative.getParent() == null
            ? ""
            : relative.getParent().toString().replace('\\', '/');

        return new GeneratedCalendarFileDTO(
            file.getFileName().toString(),
            extractVersion(relative),
            folder,
            readFileSize(file),
            readLastModified(file),
            downloadId
        );
    }

    private long readFileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read size for generated calendar: " + file, exception);
        }
    }

    private Instant readLastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read last modified for generated calendar: " + file, exception);
        }
    }

    private String extractVersion(Path relativePath) {
        for (int i = relativePath.getNameCount() - 2; i >= 0; i--) {
            String segment = relativePath.getName(i).toString();
            Matcher matcher = VERSION_PATTERN.matcher(segment);
            if (matcher.matches()) {
                return "v" + matcher.group(1);
            }
        }

        String fileName = relativePath.getFileName().toString();
        Matcher fileMatcher = Pattern.compile(".*-v(\\d+)\\.xlsx$", Pattern.CASE_INSENSITIVE).matcher(fileName);
        if (fileMatcher.matches()) {
            return "v" + fileMatcher.group(1);
        }

        return "base";
    }

    private Path decodeDownloadPath(String downloadId, Path seasonDirectory) {
        if (!StringUtils.hasText(downloadId)) {
            throw new IllegalArgumentException("Invalid downloadId");
        }

        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(downloadId), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid downloadId", exception);
        }

        Path relative = Path.of(decoded).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IllegalArgumentException("Invalid download path");
        }

        Path resolved = seasonDirectory.resolve(relative).normalize();
        if (!resolved.startsWith(seasonDirectory)) {
            throw new IllegalArgumentException("Invalid download path");
        }
        return resolved;
    }

    private String resolveStorageDirectoryName(String competitionId) {
        var competitionConfig = findCompetitionConfig(competitionId);
        if (competitionConfig == null) {
            throw new IllegalArgumentException("Unknown competitionId: " + competitionId);
        }

        if (StringUtils.hasText(competitionConfig.getStorageDir())) {
            return sanitizeStorageDirectory(competitionConfig.getStorageDir());
        }

        if (StringUtils.hasText(competitionConfig.getFile())) {
            Path configuredFilePath = Path.of(competitionConfig.getFile()).normalize();
            Path parent = configuredFilePath.getParent();
            if (parent != null) {
                return sanitizeStorageDirectory(parent.toString());
            }
        }

        throw new IllegalStateException(
            "Missing storageDir for competitionId " + competitionId
                + ". Configure calendar.competitions." + competitionId + ".storage-dir"
        );
    }

    private CalendarCompetitionsProperties.CompetitionConfig findCompetitionConfig(String competitionId) {
        for (var entry : calendarCompetitionsProperties.getCompetitions().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(competitionId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String sanitizeStorageDirectory(String rawDirectory) {
        String normalized = rawDirectory.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (!StringUtils.hasText(normalized) || normalized.contains("..")) {
            throw new IllegalArgumentException("Invalid storage directory: " + rawDirectory);
        }

        return normalized;
    }
}

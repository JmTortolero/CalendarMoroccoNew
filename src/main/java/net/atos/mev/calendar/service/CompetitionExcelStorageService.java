package net.atos.mev.calendar.service;

import java.io.IOException;
import java.io.InputStream;
import jakarta.annotation.PostConstruct;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import net.atos.mev.calendar.config.CalendarCompetitionsProperties;
import net.atos.mev.calendar.service.dto.CompetitionExcelFileDTO;

@Service
public class CompetitionExcelStorageService {

    private static final Logger log = LoggerFactory.getLogger(CompetitionExcelStorageService.class);
    private static final DateTimeFormatter VERSION_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String COMPETITION_ID_REGEX = "^[A-Za-z0-9_-]+$";
    private static final String SEASON_REGEX = "^[A-Za-z0-9][A-Za-z0-9._-]*$";
    private static final String FILE_NAME_REGEX = "^[A-Za-z0-9._() -]+(?i)\\.xlsx$";

    private final Path baseDirectory;
    private final ResourcePatternResolver resourcePatternResolver;
    private final CalendarCompetitionsProperties calendarCompetitionsProperties;
    private final CompetitionPropertiesService competitionPropertiesService;

    public CompetitionExcelStorageService(
        @Value("${calendar.storage.base-dir:./data/calendar}") String baseDirectory,
        ResourcePatternResolver resourcePatternResolver,
        CalendarCompetitionsProperties calendarCompetitionsProperties,
        CompetitionPropertiesService competitionPropertiesService
    ) {
        this.baseDirectory = Path.of(baseDirectory).toAbsolutePath().normalize();
        this.resourcePatternResolver = resourcePatternResolver;
        this.calendarCompetitionsProperties = calendarCompetitionsProperties;
        this.competitionPropertiesService = competitionPropertiesService;
    }

    @PostConstruct
    public void initializeStorageStructure() {
        try {
            Files.createDirectories(baseDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create base storage directory: " + baseDirectory, exception);
        }

        for (var entry : calendarCompetitionsProperties.getCompetitions().entrySet()) {
            String competitionId = entry.getKey();
            try {
                String storageDirectory = resolveStorageDirectoryName(competitionId);
                Files.createDirectories(baseDirectory.resolve(storageDirectory));

                String datesFile = competitionPropertiesService
                    .loadFinalProperties(competitionId)
                    .getProperty("datesFile");
                String season = extractSeasonFromDatesFile(datesFile);
                if (season != null) {
                    Files.createDirectories(baseDirectory.resolve(storageDirectory).resolve(season));
                }
            } catch (Exception exception) {
                log.warn(
                    "Skipping storage bootstrap for competitionId {}. Cause: {}",
                    competitionId,
                    exception.getMessage()
                );
            }
        }
    }

    public List<CompetitionExcelFileDTO> listExcelFiles(String competitionId, String season) {
        Path seasonDirectory = resolveSeasonDirectory(competitionId, season);
        Map<String, CompetitionExcelFileDTO> combinedFiles = new LinkedHashMap<>();

        if (Files.exists(seasonDirectory)) {
            try (Stream<Path> files = Files.list(seasonDirectory)) {
                files
                    .filter(Files::isRegularFile)
                    .filter(path -> isValidExcelFileName(path.getFileName().toString()))
                    .map(this::toExcelFileDTO)
                    .forEach(file -> combinedFiles.put(file.fileName(), file));
            } catch (IOException exception) {
                throw new IllegalStateException(
                    "Cannot list excel files for competitionId " + competitionId + " and season " + season,
                    exception
                );
            }
        }

        for (Resource resource : findClasspathSeasonResources(competitionId, season)) {
            CompetitionExcelFileDTO dto = toExcelFileDTO(resource);
            combinedFiles.putIfAbsent(dto.fileName(), dto);
        }

        return combinedFiles.values().stream()
            .sorted(Comparator.comparing(CompetitionExcelFileDTO::fileName))
            .toList();
    }

    public CompetitionExcelFileDTO storeExcelFile(
        String competitionId,
        String season,
        MultipartFile excel,
        boolean overwrite
    ) {
        if (excel == null || excel.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required");
        }

        String originalFileName = excel.getOriginalFilename();
        if (!isValidExcelFileName(originalFileName)) {
            throw new IllegalArgumentException("Invalid excel file name: " + originalFileName);
        }

        Path seasonDirectory = resolveSeasonDirectory(competitionId, season);
        Path target = seasonDirectory.resolve(originalFileName).normalize();
        Path temp = seasonDirectory.resolve(originalFileName + ".tmp").normalize();

        try {
            Files.createDirectories(seasonDirectory);
            if (Files.exists(target) && !overwrite) {
                throw new IllegalArgumentException("Excel file already exists: " + originalFileName);
            }

            try (InputStream inputStream = excel.getInputStream()) {
                Files.copy(inputStream, temp, StandardCopyOption.REPLACE_EXISTING);
            }

            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Cannot store excel file for competitionId " + competitionId + " and season " + season,
                exception
            );
        }

        return toExcelFileDTO(target);
    }

    public Optional<byte[]> readExcelFile(String competitionId, String season, String fileName) {
        if (!isValidExcelFileName(fileName)) {
            throw new IllegalArgumentException("Invalid excel file name: " + fileName);
        }

        Path seasonDirectory = resolveSeasonDirectory(competitionId, season);
        Path target = seasonDirectory.resolve(fileName).normalize();
        ensurePathInsideBase(target);

        if (!Files.exists(target)) {
            Resource classpathResource = findClasspathExcelResource(competitionId, season, fileName);
            if (classpathResource == null || !classpathResource.exists()) {
                return Optional.empty();
            }

            try (InputStream inputStream = classpathResource.getInputStream()) {
                return Optional.of(inputStream.readAllBytes());
            } catch (IOException exception) {
                throw new IllegalStateException(
                    "Cannot read classpath excel file " + fileName + " for competitionId " + competitionId + " and season " + season,
                    exception
                );
            }
        }

        try {
            return Optional.of(Files.readAllBytes(target));
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Cannot read excel file " + fileName + " for competitionId " + competitionId + " and season " + season,
                exception
            );
        }
    }

    public String buildAutoVersionName(String baseNameWithoutExtension) {
        String safeBase = (baseNameWithoutExtension == null || baseNameWithoutExtension.isBlank())
            ? "calendar"
            : baseNameWithoutExtension.replaceAll("[^A-Za-z0-9._-]", "_");
        String timestamp = LocalDateTime.now().format(VERSION_TIMESTAMP);
        return safeBase + "-" + timestamp + ".xlsx";
    }

    private Path resolveSeasonDirectory(String competitionId, String season) {
        if (competitionId == null || !competitionId.matches(COMPETITION_ID_REGEX)) {
            throw new IllegalArgumentException("Invalid competitionId: " + competitionId);
        }
        if (season == null || !season.matches(SEASON_REGEX)) {
            throw new IllegalArgumentException("Invalid season: " + season);
        }

        String storageDirectory = resolveStorageDirectoryName(competitionId);
        Path resolved = baseDirectory.resolve(storageDirectory).resolve(season).normalize();
        ensurePathInsideBase(resolved);
        return resolved;
    }

    private void ensurePathInsideBase(Path resolved) {
        if (!resolved.startsWith(baseDirectory)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
    }

    private boolean isValidExcelFileName(String fileName) {
        return fileName != null && fileName.matches(FILE_NAME_REGEX);
    }

    private Resource findClasspathExcelResource(String competitionId, String season, String fileName) {
        return findClasspathSeasonResources(competitionId, season).stream()
            .filter(resource -> resource.getFilename() != null && resource.getFilename().equals(fileName))
            .findFirst()
            .orElse(null);
    }

    private List<Resource> findClasspathSeasonResources(String competitionId, String season) {
        String seasonFolder = resolveSeasonFolderForClasspath(competitionId, season);
        if (seasonFolder == null) {
            return List.of();
        }

        String pattern = "classpath*:" + seasonFolder + "/*.xlsx";
        try {
            Resource[] resources = resourcePatternResolver.getResources(pattern);
            return List.of(resources);
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Cannot scan classpath excels for competitionId " + competitionId + " and season " + season,
                exception
            );
        }
    }

    private String resolveSeasonFolderForClasspath(String competitionId, String season) {
        try {
            String datesFile = competitionPropertiesService.loadFinalProperties(competitionId).getProperty("datesFile");
            if (datesFile != null && datesFile.contains("/" + season + "/")) {
                int lastSlash = datesFile.lastIndexOf('/');
                if (lastSlash > 0) {
                    return datesFile.substring(0, lastSlash);
                }
            }
        } catch (Exception ignored) {
            // Fallback to deriving from competition file path.
        }

        var competitionConfig = findCompetitionConfig(competitionId);
        if (competitionConfig != null && competitionConfig.getFile() != null && competitionConfig.getFile().contains("/")) {
            int slashIndex = competitionConfig.getFile().lastIndexOf('/');
            if (slashIndex > 0) {
                return competitionConfig.getFile().substring(0, slashIndex) + "/" + season;
            }
        }
        return null;
    }

    private String resolveStorageDirectoryName(String competitionId) {
        CalendarCompetitionsProperties.CompetitionConfig competitionConfig = findCompetitionConfig(competitionId);
        if (competitionConfig != null) {
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
        if (!StringUtils.hasText(rawDirectory)) {
            throw new IllegalArgumentException("Invalid storage directory");
        }

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

    private String extractSeasonFromDatesFile(String datesFile) {
        if (!StringUtils.hasText(datesFile)) {
            return null;
        }

        String normalized = datesFile.replace('\\', '/');
        String[] segments = normalized.split("/");
        if (segments.length < 3) {
            return null;
        }

        return segments[segments.length - 2];
    }

    private CompetitionExcelFileDTO toExcelFileDTO(Path filePath) {
        try {
            return new CompetitionExcelFileDTO(
                filePath.getFileName().toString(),
                Files.size(filePath),
                Files.getLastModifiedTime(filePath).toInstant()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read metadata for file: " + filePath.getFileName(), exception);
        }
    }

    private CompetitionExcelFileDTO toExcelFileDTO(Resource resource) {
        String fileName = resource.getFilename();
        if (fileName == null) {
            throw new IllegalStateException("Classpath excel resource without filename: " + resource);
        }

        long size = -1L;
        try {
            size = resource.contentLength();
        } catch (IOException ignored) {
        }

        var lastModified = (java.time.Instant) null;
        try {
            lastModified = java.time.Instant.ofEpochMilli(resource.lastModified());
        } catch (IOException ignored) {
        }

        return new CompetitionExcelFileDTO(fileName, size, lastModified);
    }
}

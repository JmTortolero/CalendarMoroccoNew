package net.atos.mev.calendarcalculator.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import net.atos.mev.calendarcalculator.config.CalendarCompetitionsProperties;
import net.atos.mev.calendarcalculator.service.dto.SeasonDTO;

@Service
public class SeasonCatalogService {

    private static final Pattern SEASON_PATTERN = Pattern.compile("^\\d{4}-\\d{2}$");
    private static final Pattern SEASON_FROM_PATH_PATTERN = Pattern.compile("/(\\d{4}-\\d{2})/[^/]+\\.xlsx$");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private final CalendarCompetitionsProperties calendarCompetitionsProperties;
    private final CompetitionPropertiesService competitionPropertiesService;
    private final ResourcePatternResolver resourcePatternResolver;
    private final Path baseDirectory;

    public SeasonCatalogService(
        CalendarCompetitionsProperties calendarCompetitionsProperties,
        CompetitionPropertiesService competitionPropertiesService,
        ResourcePatternResolver resourcePatternResolver,
        @Value("${calendar.storage.base-dir:./data}") String baseDirectory
    ) {
        this.calendarCompetitionsProperties = calendarCompetitionsProperties;
        this.competitionPropertiesService = competitionPropertiesService;
        this.resourcePatternResolver = resourcePatternResolver;
        this.baseDirectory = Path.of(baseDirectory).toAbsolutePath().normalize();
    }

    public List<SeasonDTO> listSeasons() {
        Set<String> seasonIds = new LinkedHashSet<>();

        for (var entry : calendarCompetitionsProperties.getCompetitions().entrySet()) {
            String competitionId = entry.getKey();
            var competition = entry.getValue();
            String storageDirectory = normalizeStorageDirectory(competition.getStorageDir());

            if (storageDirectory != null) {
                seasonIds.addAll(findSeasonsInFilesystem(storageDirectory));
                seasonIds.addAll(findSeasonsInClasspath(storageDirectory));
            }

            seasonIds.add(extractSeasonFromDatesFile(resolveDatesFile(competitionId)));
            seasonIds.add(buildSeasonFromStartEnd(competition.getStart(), competition.getEnd()));
        }

        List<String> ordered = seasonIds.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .filter(this::isSeasonId)
            .sorted(this::compareSeasonDesc)
            .toList();

        String currentSeason = resolveCurrentSeasonId();
        if (!ordered.contains(currentSeason) && !ordered.isEmpty()) {
            currentSeason = ordered.get(0);
        }

        List<SeasonDTO> seasons = new ArrayList<>(ordered.size());
        for (String seasonId : ordered) {
            seasons.add(new SeasonDTO(seasonId, seasonId, true, seasonId.equals(currentSeason)));
        }
        return seasons;
    }

    private List<String> findSeasonsInFilesystem(String storageDirectory) {
        Path competitionDirectory = baseDirectory.resolve(storageDirectory).normalize();
        if (!competitionDirectory.startsWith(baseDirectory) || !Files.isDirectory(competitionDirectory)) {
            return List.of();
        }

        try (Stream<Path> entries = Files.list(competitionDirectory)) {
            return entries
                .filter(Files::isDirectory)
                .map(path -> path.getFileName().toString())
                .filter(this::isSeasonId)
                .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    private List<String> findSeasonsInClasspath(String storageDirectory) {
        String pattern = "classpath*:" + storageDirectory + "/*/*.xlsx";
        try {
            Resource[] resources = resourcePatternResolver.getResources(pattern);
            Set<String> seasons = new LinkedHashSet<>();
            for (Resource resource : resources) {
                String fromPath = extractSeasonFromResourcePath(resource);
                if (isSeasonId(fromPath)) {
                    seasons.add(fromPath);
                }
            }
            return List.copyOf(seasons);
        } catch (IOException exception) {
            return List.of();
        }
    }

    private String extractSeasonFromResourcePath(Resource resource) {
        try {
            String path = resource.getURL().toString().replace('\\', '/');
            Matcher matcher = SEASON_FROM_PATH_PATTERN.matcher(path);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private String resolveDatesFile(String competitionId) {
        try {
            return competitionPropertiesService.loadFinalProperties(competitionId).getProperty("datesFile");
        } catch (Exception ignored) {
            return null;
        }
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

        String season = segments[segments.length - 2];
        return isSeasonId(season) ? season : null;
    }

    private String buildSeasonFromStartEnd(String start, String end) {
        if (!StringUtils.hasText(start) || !StringUtils.hasText(end)) {
            return null;
        }

        try {
            LocalDate startDate = LocalDate.parse(start.trim(), DATE_FORMATTER);
            LocalDate endDate = LocalDate.parse(end.trim(), DATE_FORMATTER);
            int startYear = startDate.getYear();
            int endYear = endDate.getYear() % 100;
            return String.format(Locale.ROOT, "%d-%02d", startYear, endYear);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String resolveCurrentSeasonId() {
        LocalDate today = LocalDate.now();
        int startYear = today.getMonthValue() >= Month.JULY.getValue() ? today.getYear() : today.getYear() - 1;
        int endYear = (startYear + 1) % 100;
        return String.format(Locale.ROOT, "%d-%02d", startYear, endYear);
    }

    private String normalizeStorageDirectory(String storageDirectory) {
        if (!StringUtils.hasText(storageDirectory)) {
            return null;
        }

        String normalized = storageDirectory.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (!StringUtils.hasText(normalized) || normalized.contains("..")) {
            return null;
        }

        return normalized;
    }

    private boolean isSeasonId(String value) {
        return value != null && SEASON_PATTERN.matcher(value).matches();
    }

    private int compareSeasonDesc(String left, String right) {
        return Comparator.comparingInt(this::toSortKey).reversed().compare(left, right);
    }

    private int toSortKey(String season) {
        Matcher matcher = SEASON_PATTERN.matcher(season);
        if (!matcher.matches()) {
            return 0;
        }
        int startYear = Integer.parseInt(season.substring(0, 4));
        int endYear = Integer.parseInt(season.substring(5, 7));
        return startYear * 100 + endYear;
    }
}

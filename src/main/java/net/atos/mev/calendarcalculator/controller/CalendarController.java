package net.atos.mev.calendarcalculator.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import net.atos.mev.calendarcalculator.service.CalendarGenerationService;
import net.atos.mev.calendarcalculator.service.CompetitionCatalogService;
import net.atos.mev.calendarcalculator.service.CompetitionExcelStorageService;
import net.atos.mev.calendarcalculator.service.GeneratedCalendarStorageService;
import net.atos.mev.calendarcalculator.service.GenerationFailedException;
import net.atos.mev.calendarcalculator.service.GenerationLogStoreService;
import net.atos.mev.calendarcalculator.service.dto.CompetitionExcelFileDTO;
import net.atos.mev.calendarcalculator.service.dto.GeneratedCalendarFileDTO;
import net.atos.mev.calendarcalculator.service.dto.GenerationLogsDTO;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private static final Logger log = LoggerFactory.getLogger(CalendarController.class);

    private static final MediaType XLSX_MEDIA_TYPE =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final CompetitionCatalogService competitionCatalogService;
    private final CompetitionExcelStorageService competitionExcelStorageService;
    private final GeneratedCalendarStorageService generatedCalendarStorageService;
    private final CalendarGenerationService calendarGenerationService;
    private final GenerationLogStoreService generationLogStoreService;

    public CalendarController(
        CompetitionCatalogService competitionCatalogService,
        CompetitionExcelStorageService competitionExcelStorageService,
        GeneratedCalendarStorageService generatedCalendarStorageService,
        CalendarGenerationService calendarGenerationService,
        GenerationLogStoreService generationLogStoreService
    ) {
        this.competitionCatalogService = competitionCatalogService;
        this.competitionExcelStorageService = competitionExcelStorageService;
        this.generatedCalendarStorageService = generatedCalendarStorageService;
        this.calendarGenerationService = calendarGenerationService;
        this.generationLogStoreService = generationLogStoreService;
    }

    @GetMapping("/competitions/{competitionId}/seasons/{season}/excels")
    public List<CompetitionExcelFileDTO> listExcelFiles(
        @PathVariable String competitionId,
        @PathVariable String season
    ) {
        ensureCompetitionExists(competitionId);
        return competitionExcelStorageService.listExcelFiles(competitionId, season);
    }

    @PostMapping(value = "/competitions/{competitionId}/seasons/{season}/excels", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompetitionExcelFileDTO uploadExcelFile(
        @PathVariable String competitionId,
        @PathVariable String season,
        @RequestParam("excel") MultipartFile excel,
        @RequestParam(value = "overwrite", required = false, defaultValue = "true") boolean overwrite
    ) {
        try {
            ensureCompetitionExists(competitionId);
            return competitionExcelStorageService.storeExcelFile(competitionId, season, excel, overwrite);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/competitions/{competitionId}/seasons/{season}/excels/{fileName:.+}")
    public ResponseEntity<byte[]> downloadExcelFile(
        @PathVariable String competitionId,
        @PathVariable String season,
        @PathVariable String fileName
    ) {
        ensureCompetitionExists(competitionId);
        byte[] excelBytes = competitionExcelStorageService.readExcelFile(competitionId, season, fileName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Excel not found: " + fileName));

        return ResponseEntity.ok()
            .contentType(XLSX_MEDIA_TYPE)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
            .body(excelBytes);
    }

    @PostMapping("/generate")
    public ResponseEntity<byte[]> generateCalendar(
        @RequestParam("competitionId") String competitionId,
        @RequestParam("season") String season,
        @RequestParam("excelFileName") String excelFileName,
        @RequestParam(value = "lastRoundToAssign", required = false) Integer lastRoundToAssign
    ) {
        try {
            ensureCompetitionExists(competitionId);

            var generationResult = calendarGenerationService.generateCalendarWithLogs(
                competitionId,
                season,
                excelFileName,
                lastRoundToAssign
            );

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String filename = "calendar-" + competitionId + "-" + timestamp + ".xlsx";

            return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header("X-Generation-Id", generationResult.generationId())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(generationResult.generatedExcel());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/generations/{generationId}/logs")
    public GenerationLogsDTO generationLogs(@PathVariable String generationId) {
        return generationLogStoreService.getLogs(generationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Generation not found: " + generationId));
    }

    @GetMapping("/competitions/{competitionId}/seasons/{season}/generated-full-calendars")
    public List<GeneratedCalendarFileDTO> listGeneratedFullCalendars(
        @PathVariable String competitionId,
        @PathVariable String season
    ) {
        ensureCompetitionExists(competitionId);
        try {
            return generatedCalendarStorageService.listGeneratedFullCalendars(competitionId, season);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/competitions/{competitionId}/seasons/{season}/generated-full-calendars/{downloadId}")
    public ResponseEntity<byte[]> downloadGeneratedFullCalendar(
        @PathVariable String competitionId,
        @PathVariable String season,
        @PathVariable String downloadId
    ) {
        ensureCompetitionExists(competitionId);
        try {
            byte[] excelBytes = generatedCalendarStorageService.readGeneratedFullCalendar(competitionId, season, downloadId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Generated full calendar not found"));

            String fileName = "fullCalendar.xlsx";
            var generatedFiles = generatedCalendarStorageService.listGeneratedFullCalendars(competitionId, season);
            for (GeneratedCalendarFileDTO file : generatedFiles) {
                if (file.downloadId().equals(downloadId)) {
                    fileName = file.fileName();
                    break;
                }
            }

            return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(excelBytes);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private void ensureCompetitionExists(String competitionId) {
        competitionCatalogService.getCompetitionById(competitionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Competition not found: " + competitionId));
    }

    @ExceptionHandler(GenerationFailedException.class)
    public ResponseEntity<String> handleGenerationFailedException(GenerationFailedException exception) {
        log.error("Calendar generation failed. generationId={}", exception.getGenerationId(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.TEXT_PLAIN)
            .header("X-Generation-Id", exception.getGenerationId())
            .body(exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleGenerationError(IllegalStateException exception) {
        log.error("Calendar generation failed", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.TEXT_PLAIN)
            .body(exception.getMessage());
    }
}

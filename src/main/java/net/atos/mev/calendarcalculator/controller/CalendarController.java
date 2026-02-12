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
import net.atos.mev.calendarcalculator.service.dto.CompetitionExcelFileDTO;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private static final Logger log = LoggerFactory.getLogger(CalendarController.class);

    private static final MediaType XLSX_MEDIA_TYPE =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final CompetitionCatalogService competitionCatalogService;
    private final CompetitionExcelStorageService competitionExcelStorageService;
    private final CalendarGenerationService calendarGenerationService;

    public CalendarController(
        CompetitionCatalogService competitionCatalogService,
        CompetitionExcelStorageService competitionExcelStorageService,
        CalendarGenerationService calendarGenerationService
    ) {
        this.competitionCatalogService = competitionCatalogService;
        this.competitionExcelStorageService = competitionExcelStorageService;
        this.calendarGenerationService = calendarGenerationService;
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
        ensureCompetitionExists(competitionId);
        return competitionExcelStorageService.storeExcelFile(competitionId, season, excel, overwrite);
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

            byte[] generatedExcel = calendarGenerationService.generateCalendar(
                competitionId,
                season,
                excelFileName,
                lastRoundToAssign
            );

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String filename = "calendar-" + competitionId + "-" + timestamp + ".xlsx";

            return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(generatedExcel);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private void ensureCompetitionExists(String competitionId) {
        competitionCatalogService.getCompetitionById(competitionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Competition not found: " + competitionId));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleGenerationError(IllegalStateException exception) {
        log.error("Calendar generation failed", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.TEXT_PLAIN)
            .body(exception.getMessage());
    }
}

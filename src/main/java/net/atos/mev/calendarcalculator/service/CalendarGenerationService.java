package net.atos.mev.calendarcalculator.service;

import java.io.ByteArrayInputStream;
import java.util.Properties;

import org.springframework.stereotype.Service;

import net.atos.mev.calendarcalculator.service.dto.CompetitionRuntimeOverridesDTO;

@Service
public class CalendarGenerationService {

    private final CompetitionPropertiesService competitionPropertiesService;
    private final CompetitionExcelStorageService competitionExcelStorageService;
    private final ScheduleFacade scheduleFacade;

    public CalendarGenerationService(
        CompetitionPropertiesService competitionPropertiesService,
        CompetitionExcelStorageService competitionExcelStorageService,
        ScheduleFacade scheduleFacade
    ) {
        this.competitionPropertiesService = competitionPropertiesService;
        this.competitionExcelStorageService = competitionExcelStorageService;
        this.scheduleFacade = scheduleFacade;
    }

    public byte[] generateCalendar(
        String competitionId,
        String season,
        String excelFileName,
        Integer lastRoundToAssign
    ) {
        if (excelFileName == null || excelFileName.isBlank()) {
            throw new IllegalArgumentException("excelFileName is required");
        }

        CompetitionRuntimeOverridesDTO runtimeOverrides = new CompetitionRuntimeOverridesDTO(
            null,
            null,
            lastRoundToAssign
        );

        Properties finalProperties = competitionPropertiesService.loadFinalProperties(competitionId, runtimeOverrides);

        byte[] excelBytes = competitionExcelStorageService.readExcelFile(competitionId, season, excelFileName)
            .orElseThrow(() -> new IllegalArgumentException(
                "Excel file not found for competitionId " + competitionId + ", season " + season + ": " + excelFileName
            ));

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(excelBytes)) {
            return scheduleFacade.run(finalProperties, inputStream);
        } catch (Exception exception) {
            Throwable root = exception;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            String rootMessage = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
            throw new IllegalStateException(
                "Error generating calendar for competitionId " + competitionId
                    + ", season " + season
                    + ", excelFileName " + excelFileName
                    + ". Root cause: " + rootMessage,
                exception
            );
        }
    }
}

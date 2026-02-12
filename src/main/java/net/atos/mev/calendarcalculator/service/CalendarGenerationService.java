package net.atos.mev.calendarcalculator.service;

import java.io.ByteArrayInputStream;
import java.util.Properties;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import net.atos.mev.calendarcalculator.service.dto.CompetitionRuntimeOverridesDTO;
import net.atos.mev.calendarcalculator.service.dto.GenerationExecutionResult;

@Service
public class CalendarGenerationService {

    private static final String LOG_SCOPE = "net.atos.mev.calendarcalculator";

    private final CompetitionPropertiesService competitionPropertiesService;
    private final CompetitionExcelStorageService competitionExcelStorageService;
    private final ScheduleFacade scheduleFacade;
    private final GenerationLogStoreService generationLogStoreService;

    public CalendarGenerationService(
        CompetitionPropertiesService competitionPropertiesService,
        CompetitionExcelStorageService competitionExcelStorageService,
        ScheduleFacade scheduleFacade,
        GenerationLogStoreService generationLogStoreService
    ) {
        this.competitionPropertiesService = competitionPropertiesService;
        this.competitionExcelStorageService = competitionExcelStorageService;
        this.scheduleFacade = scheduleFacade;
        this.generationLogStoreService = generationLogStoreService;
    }

    public GenerationExecutionResult generateCalendarWithLogs(
        String competitionId,
        String season,
        String excelFileName,
        Integer lastRoundToAssign
    ) {
        if (excelFileName == null || excelFileName.isBlank()) {
            throw new IllegalArgumentException("excelFileName is required");
        }

        String generationId = generationLogStoreService.startSession(competitionId, season, excelFileName);
        SessionLogAppender appender = null;
        Logger scopedLogger = null;
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            scopedLogger = loggerContext.getLogger(LOG_SCOPE);
            appender = new SessionLogAppender(generationId, Thread.currentThread().getName(), generationLogStoreService);
            appender.setContext(loggerContext);
            appender.start();
            scopedLogger.addAppender(appender);

            byte[] generatedExcel = generateCalendar(competitionId, season, excelFileName, lastRoundToAssign);
            generationLogStoreService.markSuccess(generationId);
            return new GenerationExecutionResult(generationId, generatedExcel);
        } catch (IllegalArgumentException exception) {
            generationLogStoreService.markFailure(generationId, exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            Throwable root = exception;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            String rootMessage = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
            String errorMessage = "Error generating calendar for competitionId " + competitionId
                + ", season " + season
                + ", excelFileName " + excelFileName
                + ". Root cause: " + rootMessage;
            generationLogStoreService.markFailure(generationId, errorMessage);
            throw new GenerationFailedException(generationId, errorMessage, exception);
        } finally {
            if (scopedLogger != null && appender != null) {
                scopedLogger.detachAppender(appender);
                appender.stop();
            }
        }
    }

    public byte[] generateCalendar(
        String competitionId,
        String season,
        String excelFileName,
        Integer lastRoundToAssign
    ) {
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

        ByteArrayInputStream inputStream = new ByteArrayInputStream(excelBytes);
        return scheduleFacade.run(finalProperties, inputStream);
    }

    private static final class SessionLogAppender extends AppenderBase<ILoggingEvent> {
        private final String generationId;
        private final String threadName;
        private final GenerationLogStoreService generationLogStoreService;

        private SessionLogAppender(String generationId, String threadName, GenerationLogStoreService generationLogStoreService) {
            this.generationId = generationId;
            this.threadName = threadName;
            this.generationLogStoreService = generationLogStoreService;
        }

        @Override
        protected void append(ILoggingEvent event) {
            if (!threadName.equals(event.getThreadName())) {
                return;
            }
            if (event.getLoggerName() == null || !event.getLoggerName().startsWith(LOG_SCOPE)) {
                return;
            }

            String message = event.getFormattedMessage();
            if (message != null) {
                generationLogStoreService.append(generationId, message);
            }
        }
    }
}

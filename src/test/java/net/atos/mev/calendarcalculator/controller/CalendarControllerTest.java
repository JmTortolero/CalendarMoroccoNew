package net.atos.mev.calendarcalculator.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import net.atos.mev.calendarcalculator.service.CalendarGenerationService;
import net.atos.mev.calendarcalculator.service.CompetitionCatalogService;
import net.atos.mev.calendarcalculator.service.CompetitionExcelStorageService;
import net.atos.mev.calendarcalculator.service.dto.CompetitionDTO;
import net.atos.mev.calendarcalculator.service.dto.CompetitionExcelFileDTO;

@WebMvcTest(CalendarController.class)
class CalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CalendarGenerationService calendarGenerationService;

    @MockBean
    private CompetitionCatalogService competitionCatalogService;

    @MockBean
    private CompetitionExcelStorageService competitionExcelStorageService;

    @Test
    void shouldListExcelsForCompetitionAndSeason() throws Exception {
        CompetitionDTO competition = new CompetitionDTO("BOTOLA_D1", "Botola Pro (1a Division)", "schBotolaD1/SchMoroccoD1.properties", true);
        when(competitionCatalogService.getCompetitionById("BOTOLA_D1")).thenReturn(Optional.of(competition));
        when(competitionExcelStorageService.listExcelFiles("BOTOLA_D1", "2025-26")).thenReturn(List.of(
            new CompetitionExcelFileDTO("CalendarD1-v0.xlsx", 123L, Instant.parse("2026-02-11T11:00:00Z")),
            new CompetitionExcelFileDTO("CalendarD1-v1.xlsx", 124L, Instant.parse("2026-02-11T12:00:00Z"))
        ));

        mockMvc.perform(get("/api/calendar/competitions/BOTOLA_D1/seasons/2025-26/excels"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].fileName").value("CalendarD1-v0.xlsx"))
            .andExpect(jsonPath("$[1].fileName").value("CalendarD1-v1.xlsx"));
    }

    @Test
    void shouldUploadExcelForCompetitionAndSeason() throws Exception {
        CompetitionDTO competition = new CompetitionDTO("BOTOLA_D1", "Botola Pro (1a Division)", "schBotolaD1/SchMoroccoD1.properties", true);
        when(competitionCatalogService.getCompetitionById("BOTOLA_D1")).thenReturn(Optional.of(competition));

        MockMultipartFile excel = new MockMultipartFile(
            "excel",
            "CalendarD1-v1.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "dummy-base".getBytes()
        );
        when(competitionExcelStorageService.storeExcelFile(eq("BOTOLA_D1"), eq("2025-26"), any(), eq(true)))
            .thenReturn(new CompetitionExcelFileDTO("CalendarD1-v1.xlsx", 555L, Instant.parse("2026-02-11T12:10:00Z")));

        mockMvc.perform(
                multipart("/api/calendar/competitions/BOTOLA_D1/seasons/2025-26/excels")
                    .file(excel)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fileName").value("CalendarD1-v1.xlsx"));

        verify(competitionExcelStorageService).storeExcelFile(eq("BOTOLA_D1"), eq("2025-26"), any(), eq(true));
    }

    @Test
    void shouldDownloadExcelForCompetitionAndSeason() throws Exception {
        CompetitionDTO competition = new CompetitionDTO("BOTOLA_D1", "Botola Pro (1a Division)", "schBotolaD1/SchMoroccoD1.properties", true);
        when(competitionCatalogService.getCompetitionById("BOTOLA_D1")).thenReturn(Optional.of(competition));
        when(competitionExcelStorageService.readExcelFile("BOTOLA_D1", "2025-26", "CalendarD1-v0.xlsx"))
            .thenReturn(Optional.of("excel-content".getBytes()));

        mockMvc.perform(get("/api/calendar/competitions/BOTOLA_D1/seasons/2025-26/excels/CalendarD1-v0.xlsx"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"CalendarD1-v0.xlsx\""));
    }

    @Test
    void shouldGenerateCalendarFromStoredExcel() throws Exception {
        CompetitionDTO competition = new CompetitionDTO("BOTOLA_D1", "Botola Pro (1a Division)", "schBotolaD1/SchMoroccoD1.properties", true);
        when(competitionCatalogService.getCompetitionById("BOTOLA_D1")).thenReturn(Optional.of(competition));
        when(calendarGenerationService.generateCalendar("BOTOLA_D1", "2025-26", "CalendarD1-v1.xlsx", 5))
            .thenReturn("xlsx-bytes".getBytes());

        mockMvc.perform(
                post("/api/calendar/generate")
                    .param("competitionId", "BOTOLA_D1")
                    .param("season", "2025-26")
                    .param("excelFileName", "CalendarD1-v1.xlsx")
                    .param("lastRoundToAssign", "5")
            )
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("calendar-BOTOLA_D1-")));
    }
}

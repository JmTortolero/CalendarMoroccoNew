package net.atos.mev.calendarcalculator.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CompetitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldListCompetitionsFromApplicationYml() throws Exception {
        mockMvc.perform(get("/api/config/competitions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("BOTOLA_D1"));
    }

    @Test
    void shouldResolveRuntimeOverrides() throws Exception {
        String body = """
            {
              "datesFile": "schBotolaD1/2025-26/CalendarD1-v0.xlsx",
              "resultsFolder": "results/test/controller",
              "lastRoundToAssign": 6
            }
            """;

        mockMvc.perform(post("/api/config/competitions/BOTOLA_D1/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("BOTOLA_D1"))
            .andExpect(jsonPath("$.datesFile").value("schBotolaD1/2025-26/CalendarD1-v0.xlsx"))
            .andExpect(jsonPath("$.resultsFolder").value("results/test/controller"))
            .andExpect(jsonPath("$.['ScheduleMoroccoAlg.lastRoundToAssign']").value("6"));
    }
}

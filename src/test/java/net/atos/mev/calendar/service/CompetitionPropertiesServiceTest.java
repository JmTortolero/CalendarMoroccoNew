package net.atos.mev.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import net.atos.mev.calendar.service.dto.CompetitionRuntimeOverridesDTO;

@SpringBootTest
class CompetitionPropertiesServiceTest {

    @Autowired
    private CompetitionPropertiesService competitionPropertiesService;

    @Test
    void shouldLoadBaseAndApplyRuntimeOverrides() {
        CompetitionRuntimeOverridesDTO runtimeOverrides = new CompetitionRuntimeOverridesDTO(
            "schBotolaD1/2025-26/CalendarD1-v0.xlsx",
            "results/test/BOTOLA_D1",
            8
        );

        Properties resolved = competitionPropertiesService.loadFinalProperties("BOTOLA_D1", runtimeOverrides);

        assertThat(resolved.getProperty("env")).isEqualTo("MoroccoDiv1.properties");
        assertThat(resolved.getProperty("start")).isEqualTo("01/08/2025");
        assertThat(resolved.getProperty("end")).isEqualTo("01/07/2026");
        assertThat(resolved.getProperty("datesFile")).isEqualTo("schBotolaD1/2025-26/CalendarD1-v0.xlsx");
        assertThat(resolved.getProperty("resultsFolder")).isEqualTo("results/test/BOTOLA_D1");
        assertThat(resolved.getProperty("ScheduleMoroccoAlg.lastRoundToAssign")).isEqualTo("8");
    }

    @Test
    void shouldFailForUnknownCompetition() {
        assertThatThrownBy(() -> competitionPropertiesService.loadFinalProperties("NO_EXISTE"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown competitionId");
    }
}

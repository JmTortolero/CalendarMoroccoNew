package net.atos.mev.calendarcalculator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CompetitionExcelStorageServiceTest {

    @Autowired
    private CompetitionExcelStorageService competitionExcelStorageService;

    @Test
    void shouldAcceptFileNameIgnoringCaseForExpectedDivision() {
        assertThatCode(() -> competitionExcelStorageService.validateExcelFileNameForCompetition("BOTOLA_D2", "calendard2-V5.xlsx"))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectFileNameWithWrongDivisionForCompetition() {
        assertThatThrownBy(() -> competitionExcelStorageService.validateExcelFileNameForCompetition("BOTOLA_D1", "CalendarD2-v1.xlsx"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CalendarD1-v<number>.xlsx");
    }

    @Test
    void shouldRejectFileNameWithInvalidPattern() {
        assertThatThrownBy(() -> competitionExcelStorageService.validateExcelFileNameForCompetition("BOTOLA_D1", "Calendar-D1-v1.xlsx"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CalendarD1-v<number>.xlsx");
    }

    @Test
    void shouldBuildResultsFolderFromSeasonStorageDirAndFileVersion() {
        String resultsFolder = competitionExcelStorageService.buildResultsFolder("BOTOLA_D2", "2025-26", "CalendarD2-v5.xlsx");
        assertThat(resultsFolder).isEqualTo("results/2025-26/schBotolaD2-v5");
    }
}

package net.atos.mev.calendar.service.dto;

public record CompetitionRuntimeOverridesDTO(
    String datesFile,
    String resultsFolder,
    Integer lastRoundToAssign
) {
    public static CompetitionRuntimeOverridesDTO empty() {
        return new CompetitionRuntimeOverridesDTO(null, null, null);
    }
}

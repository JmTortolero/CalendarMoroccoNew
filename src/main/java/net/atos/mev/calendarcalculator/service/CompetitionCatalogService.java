package net.atos.mev.calendarcalculator.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

import net.atos.mev.calendarcalculator.config.CalendarCompetitionsProperties;
import net.atos.mev.calendarcalculator.service.dto.CompetitionDTO;

@Service
public class CompetitionCatalogService {

    private final CalendarCompetitionsProperties calendarCompetitionsProperties;

    public CompetitionCatalogService(CalendarCompetitionsProperties calendarCompetitionsProperties) {
        this.calendarCompetitionsProperties = calendarCompetitionsProperties;
    }

    public List<CompetitionDTO> listCompetitions() {
        List<CompetitionDTO> competitions = new ArrayList<>();
        for (var entry : calendarCompetitionsProperties.getCompetitions().entrySet()) {
            String id = entry.getKey();
            var config = entry.getValue();
            if (config.getFile() != null && !config.getFile().isBlank()) {
                String name = (config.getName() == null || config.getName().isBlank()) ? id : config.getName();
                competitions.add(new CompetitionDTO(id, name, config.getFile(), config.isEnabled()));
            }
        }
        return competitions;
    }

    public Optional<CompetitionDTO> getCompetitionById(String id) {
        return listCompetitions().stream()
            .filter(competition -> competition.id().equalsIgnoreCase(id))
            .findFirst();
    }
}

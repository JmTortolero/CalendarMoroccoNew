package net.atos.mev.calendarcalculator.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import net.atos.mev.calendarcalculator.service.CompetitionCatalogService;
import net.atos.mev.calendarcalculator.service.CompetitionPropertiesService;
import net.atos.mev.calendarcalculator.service.SeasonCatalogService;
import net.atos.mev.calendarcalculator.service.dto.CompetitionDTO;
import net.atos.mev.calendarcalculator.service.dto.CompetitionRuntimeOverridesDTO;
import net.atos.mev.calendarcalculator.service.dto.SeasonDTO;

@RestController
@RequestMapping("/api/config")
public class CompetitionController {

    private final CompetitionCatalogService competitionCatalogService;
    private final CompetitionPropertiesService competitionPropertiesService;
    private final SeasonCatalogService seasonCatalogService;

    public CompetitionController(
        CompetitionCatalogService competitionCatalogService,
        CompetitionPropertiesService competitionPropertiesService,
        SeasonCatalogService seasonCatalogService
    ) {
        this.competitionCatalogService = competitionCatalogService;
        this.competitionPropertiesService = competitionPropertiesService;
        this.seasonCatalogService = seasonCatalogService;
    }

    @GetMapping("/competitions")
    public List<CompetitionDTO> competitions() {
        return competitionCatalogService.listCompetitions();
    }

    @GetMapping("/competitions/{id}")
    public CompetitionDTO competitionById(@PathVariable String id) {
        return competitionCatalogService.getCompetitionById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Competition not found: " + id));
    }

    @GetMapping("/seasons")
    public List<SeasonDTO> seasons() {
        return seasonCatalogService.listSeasons();
    }

    @PostMapping("/competitions/{id}/resolve")
    public Map<String, String> resolveCompetitionProperties(
        @PathVariable String id,
        @RequestBody(required = false) CompetitionRuntimeOverridesDTO runtimeOverrides
    ) {
        try {
            var resolved = competitionPropertiesService.loadFinalProperties(id, runtimeOverrides);
            Map<String, String> response = new LinkedHashMap<>();
            response.put("id", id);
            response.put("env", resolved.getProperty("env"));
            response.put("start", resolved.getProperty("start"));
            response.put("end", resolved.getProperty("end"));
            response.put("datesFile", resolved.getProperty("datesFile"));
            response.put("resultsFolder", resolved.getProperty("resultsFolder"));
            response.put("ScheduleMoroccoAlg.lastRoundToAssign", resolved.getProperty("ScheduleMoroccoAlg.lastRoundToAssign"));
            return response;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}

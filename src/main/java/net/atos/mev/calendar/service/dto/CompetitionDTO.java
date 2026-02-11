package net.atos.mev.calendar.service.dto;

public record CompetitionDTO(
    String id,
    String name,
    String properties,
    boolean enabled
) {}

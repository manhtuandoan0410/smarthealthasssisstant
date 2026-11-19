package fi.haagahelia.smarthhealth.web.dto;

public record ConditionResponse(
        String name,
        String causes,
        String symptoms,
        String treatment,
        String sourceUrl) {}
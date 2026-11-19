package fi.haagahelia.smarthhealth.web.dto;

import java.util.List;

public record SymptomSearchResponse(String disclaimer, List<ConditionResponse> possibilities) {}
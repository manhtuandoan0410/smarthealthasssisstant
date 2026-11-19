package fi.haagahelia.smarthhealth.web;

import fi.haagahelia.smarthhealth.web.dto.SymptomSearchRequest;
import fi.haagahelia.smarthhealth.web.dto.SymptomSearchResponse;
import fi.haagahelia.smarthhealth.service.NhsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/symptoms")
public class SymptomSearchController {

    private final NhsService nhsService;

    public SymptomSearchController(NhsService nhsService) {
        this.nhsService = nhsService;
    }

    // Retained endpoint: requires sign-in and a verified medical information API to return useful results.
    @PostMapping("/search")
    public SymptomSearchResponse search(@Valid @RequestBody SymptomSearchRequest request) {
        return nhsService.searchBySymptoms(request.symptoms());
    }
}

package fi.haagahelia.smarthhealth.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import fi.haagahelia.smarthhealth.service.NearbyCareService;
import fi.haagahelia.smarthhealth.web.dto.NearbyCareResponse;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;

@RestController
@Validated
@RequestMapping("/api/nearby-care")
public class NearbyCareController {

    private final NearbyCareService nearbyCareService;

    public NearbyCareController(NearbyCareService nearbyCareService) {
        this.nearbyCareService = nearbyCareService;
    }

    @GetMapping
    public List<NearbyCareResponse> search(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
            @RequestParam @Pattern(regexp = "hospital|pharmacy") String category) {
        return nearbyCareService.search(latitude, longitude, category);
    }
}
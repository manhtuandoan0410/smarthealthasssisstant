package fi.haagahelia.smarthhealth.web;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fi.haagahelia.smarthhealth.service.NhsService;
import fi.haagahelia.smarthhealth.web.dto.ConditionResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@Validated
@RequestMapping("/api")
public class NhsController {

	private final NhsService nhsService;

	public NhsController(NhsService nhsService) {
		this.nhsService = nhsService;
	}

	// Public endpoint: returns the generated NHS page URL, not scraped medical content.
	@GetMapping("/conditions")
	public ConditionResponse searchConditions(
			@RequestParam @NotBlank @Size(max = 120) String query) {
		return nhsService.searchByName(query);
	}
}

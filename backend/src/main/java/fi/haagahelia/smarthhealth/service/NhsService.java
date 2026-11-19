package fi.haagahelia.smarthhealth.service;

//import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import fi.haagahelia.smarthhealth.integration.nhs.NhsApiClient;
import fi.haagahelia.smarthhealth.web.dto.ConditionResponse;
import fi.haagahelia.smarthhealth.web.dto.SymptomSearchResponse;

@Service
public class NhsService {

	// Current condition-name search builds a page URL; the remaining constants support symptom search.
	private static final String NHS_CONDITION_URL = "https://www.nhs.uk/conditions/";
	private static final String NOT_AVAILABLE = "Information not available in the NHS response.";
	private static final String SYMPTOM_DISCLAIMER =
			"These are possible matches for educational information only, not a diagnosis. "
					+ "Contact a qualified healthcare professional for advice.";

	private final NhsApiClient nhsApiClient;

	public NhsService(NhsApiClient nhsApiClient) {
		this.nhsApiClient = nhsApiClient;
	}

	/**
	 * Current workaround: convert a condition name such as "Type 2 diabetes" into
	 * an NHS page URL. This does not call an API or check whether the page exists.
	 * The mobile app loads the returned URL, rather than receiving the page content here.
	 */
	public ConditionResponse searchByName(String query) {
		String cleanedQuery = validateQuery(query);
		String slug = cleanedQuery.toLowerCase(Locale.ROOT)
				.replaceAll("[^\\p{L}\\p{N}]+", "-")
				.replaceAll("(^-+|-+$)", "");
		if (slug.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Enter a condition name containing letters or numbers.");
		}

		// Detail fields are empty because the NHS website supplies the content in the WebView.
		return new ConditionResponse(
				cleanedQuery,
				"",
				"",
				"",
				NHS_CONDITION_URL + slug + "/#maincontent");
	}

	/**
	 * Earlier API-based implementation, retained for future work and still called by
	 * the symptom endpoint. It needs a suitable, verified medical information API;
	 * the current NHS URL workaround does not provide symptom-search results.
	 */
	public SymptomSearchResponse searchBySymptoms(List<String> requestedSymptoms) {
		if (requestedSymptoms == null || requestedSymptoms.size() < 2) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter at least two symptoms.");
		}

		// Normalize input and require at least two different symptoms before querying the provider.
		List<String> symptoms = requestedSymptoms.stream()
				.filter(symptom -> symptom != null && !symptom.isBlank())
				.map(String::trim)
				.map(symptom -> symptom.toLowerCase(Locale.ROOT))
				.distinct()
				.limit(8)
				.toList();
		if (symptoms.size() < 2) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Enter at least two different, non-blank symptoms.");
		}

		// Group returned pages by URL or name and count how often each is returned.
		Map<String, Candidate> candidates = new LinkedHashMap<>();
		for (String symptom : symptoms) {
			for (Map<String, Object> page : nhsApiClient.searchContent(symptom)) {
				ConditionResponse condition = toCondition(page, symptom);
				Candidate candidate = candidates.computeIfAbsent(identity(condition), key -> new Candidate(condition));
				candidate.matchedSymptomSearches++;
			}
		}

		// The count is only a simple ranking, not a medical assessment or diagnosis.
		List<ConditionResponse> possibilities = candidates.values().stream()
				.sorted(Comparator.comparingInt((Candidate candidate) -> candidate.matchedSymptomSearches).reversed())
				.limit(10)
				.map(candidate -> candidate.condition)
				.toList();
		return new SymptomSearchResponse(SYMPTOM_DISCLAIMER, possibilities);
	}

	private static String validateQuery(String query) {
		if (query == null || query.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a condition or symptom to search.");
		}
		String cleaned = query.trim();
		if (cleaned.length() > 120) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search text must be 120 characters or fewer.");
		}
		return cleaned;
	}

	// API-based symptom helpers below assume possible response fields; adapt them to the chosen API's schema.
	private static ConditionResponse toCondition(Map<String, Object> page, String fallbackName) {
		String name = firstText(page, "name", "headline", "title");
		if (name == null) name = fallbackName;

		String causes = firstText(page, "causes", "cause");
		if (causes == null) causes = findSection(page, List.of("cause", "causes"));

		String symptoms = firstText(page, "symptoms", "signs and symptoms");
		if (symptoms == null) symptoms = findSection(page, List.of("symptom", "signs and symptoms"));

		String treatment = firstText(page, "treatment", "treatments", "treatment and medication");
		if (treatment == null) treatment = findSection(page, List.of("treatment", "medication", "treatments"));

		String sourceUrl = firstText(page, "url", "mainEntityOfPage", "@id");
		return new ConditionResponse(
				name,
				causes == null ? NOT_AVAILABLE : causes,
				symptoms == null ? NOT_AVAILABLE : symptoms,
				treatment == null ? NOT_AVAILABLE : treatment,
				sourceUrl);
	}

	// Prefer a source URL when grouping results; fall back to the condition name.
	private static String identity(ConditionResponse condition) {
		String identity = condition.sourceUrl() == null || condition.sourceUrl().isBlank()
				? condition.name()
				: condition.sourceUrl();
		return identity.toLowerCase(Locale.ROOT);
	}

	// Try alternative field names because the old implementation has no verified response schema.
	private static String firstText(Map<?, ?> map, String... names) {
		for (String name : names) {
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (entry.getKey() instanceof String key && key.equalsIgnoreCase(name)) {
					String text = asText(entry.getValue());
					if (text != null) return text;
				}
			}
		}
		return null;
	}

	// Extract displayable text from a string, nested object, or list.
	private static String asText(Object value) {
		if (value instanceof String text && !text.isBlank()) return text.trim();
		if (value instanceof Map<?, ?> map) {
			String text = firstText(map, "text", "value", "url", "@id", "description");
			if (text != null) return text;
		}
		if (value instanceof List<?> list) {
			return list.stream().map(NhsService::asText).filter(text -> text != null)
					.reduce((first, second) -> first + "\n" + second).orElse(null);
		}
		return null;
	}

	// Search nested API data for a named section and return its text.
	private static String findSection(Object node, List<String> names) {
		if (node instanceof Map<?, ?> map) {
			String sectionName = firstText(map, "name", "title", "headline");
			if (sectionName != null) {
				String normalizedName = sectionName.toLowerCase(Locale.ROOT);
				if (names.stream().anyMatch(normalizedName::contains)) {
					String body = firstText(map, "text", "description", "articleBody", "content");
					if (body != null) return body;
				}
			}
			for (Object value : map.values()) {
				String found = findSection(value, names);
				if (found != null) return found;
			}
		} else if (node instanceof List<?> list) {
			for (Object value : list) {
				String found = findSection(value, names);
				if (found != null) return found;
			}
		}
		return null;
	}

	// Holds one grouped result and the number of times the API returned it.
	private static final class Candidate {
		private final ConditionResponse condition;
		private int matchedSymptomSearches;

		private Candidate(ConditionResponse condition) {
			this.condition = condition;
		}
	}
}

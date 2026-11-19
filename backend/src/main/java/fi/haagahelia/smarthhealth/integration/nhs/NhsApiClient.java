package fi.haagahelia.smarthhealth.integration.nhs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
// Earlier API integration: still used by symptom search, but not verified against a working provider.
// A future medical information API will require compatible request and response handling here.
public class NhsApiClient {

	private final RestClient restClient;
	private final String subscriptionKey;
	private final String apiVersion;
	private final String searchPath;
	private final String queryParameter;

	public NhsApiClient(
			@Qualifier("nhsApiRestClient") RestClient restClient,
			@Value("${nhs.api.subscription-key:}") String subscriptionKey,
			@Value("${nhs.api.api-version:2}") String apiVersion,
			@Value("${nhs.api.search-path:/search}") String searchPath,
			@Value("${nhs.api.query-parameter:query}") String queryParameter) {
		this.restClient = restClient;
		this.subscriptionKey = subscriptionKey;
		this.apiVersion = apiVersion;
		this.searchPath = searchPath.startsWith("/") ? searchPath : "/" + searchPath;
		this.queryParameter = queryParameter;
	}

	public List<Map<String, Object>> searchContent(String term) {
		if (subscriptionKey == null || subscriptionKey.isBlank()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"NHS condition search is not configured.");
		}

		// The path, parameters and subscription header reflect assumptions, not a verified API contract.
		try {
			Object response = restClient.get()
					.uri(uriBuilder -> uriBuilder.path(searchPath)
							.queryParam("api-version", apiVersion)
							.queryParam(queryParameter, term)
							.build())
					.header("subscription-key", subscriptionKey)
					.retrieve()
					.body(Object.class);
			return extractResults(response);
		} catch (RestClientException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"The NHS content service could not complete the search.");
		}
	}

	// Accept several possible response wrappers; replace these assumptions when choosing an API.
	private static List<Map<String, Object>> extractResults(Object response) {
		if (response instanceof List<?> list) {
			return mapsFrom(list);
		}
		if (!(response instanceof Map<?, ?> map)) {
			return List.of();
		}

		for (String key : List.of("results", "items", "value", "documents", "conditions", "searchResults")) {
			Object nested = map.get(key);
			if (nested instanceof List<?> list) {
				return mapsFrom(list);
			}
		}
		return List.of(stringKeyMap(map));
	}

	private static List<Map<String, Object>> mapsFrom(List<?> values) {
		List<Map<String, Object>> maps = new ArrayList<>();
		for (Object value : values) {
			if (value instanceof Map<?, ?> map) {
				maps.add(stringKeyMap(map));
			}
		}
		return maps;
	}

	private static Map<String, Object> stringKeyMap(Map<?, ?> source) {
		Map<String, Object> result = new LinkedHashMap<>();
		source.forEach((key, value) -> {
			if (key instanceof String stringKey) {
				result.put(stringKey, value);
			}
		});
		return result;
	}
}

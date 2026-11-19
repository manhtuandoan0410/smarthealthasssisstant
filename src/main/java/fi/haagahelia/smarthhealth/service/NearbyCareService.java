package fi.haagahelia.smarthhealth.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import fi.haagahelia.smarthhealth.web.dto.NearbyCareResponse;

@Service
public class NearbyCareService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NearbyCareService.class);
    private static final String FIELD_MASK = "places.id,places.displayName,places.formattedAddress,"
            + "places.nationalPhoneNumber,places.location,places.googleMapsUri,places.rating";

    private final RestClient restClient;
    private final String apiKey;

    @Autowired
    public NearbyCareService(@Value("${google.places.api-key:}") String apiKey) {
        this(apiKey, RestClient.create("https://places.googleapis.com/v1"));
    }

    NearbyCareService(String apiKey, RestClient restClient) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    public List<NearbyCareResponse> search(double latitude, double longitude, String category) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Nearby care search is not configured.");
        }

        String placeType = switch (category) {
            case "hospital" -> "hospital";
            case "pharmacy" -> "pharmacy";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose hospitals or pharmacies.");
        };
        Map<String, Object> request = Map.of(
                "includedTypes", List.of(placeType),
                "maxResultCount", 20,
                "locationRestriction", Map.of("circle", Map.of(
                        "center", Map.of("latitude", latitude, "longitude", longitude),
                        "radius", 2000)));

        try {
                GooglePlacesResponse response = restClient.post()
                    .uri("/places:searchNearby")
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .body(request)
                    .retrieve()
                    .body(GooglePlacesResponse.class);
            return mapPlaces(response);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
                String reason = googleErrorReason(exception);
                LOGGER.warn("Nearby care provider denied the request (HTTP {}, reason {}).",
                        exception.getStatusCode().value(), reason);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        googleErrorMessage(reason), exception);
            }
            LOGGER.warn("Nearby care provider returned HTTP {}.", exception.getStatusCode().value());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The nearby-care provider could not complete the search.", exception);
        } catch (RestClientException exception) {
            LOGGER.warn("Nearby care provider connection failed ({}).", exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The nearby-care provider could not complete the search.", exception);
        }
    }

    private static String googleErrorReason(RestClientResponseException exception) {
        Map<?, ?> response;
        try {
            response = exception.getResponseBodyAs(Map.class);
        } catch (RestClientException | IllegalStateException parseError) {
            LOGGER.warn("Could not read the nearby care provider's error response.");
            return "UNKNOWN";
        }
        if (response == null || !(response.get("error") instanceof Map<?, ?> error)
                || !(error.get("details") instanceof List<?> details)) {
            return "UNKNOWN";
        }
        for (Object detail : details) {
            if (detail instanceof Map<?, ?> map && map.get("reason") instanceof String reason) {
                return switch (reason) {
                    case "API_KEY_ANDROID_APP_BLOCKED", "API_KEY_IOS_APP_BLOCKED",
                            "API_KEY_HTTP_REFERRER_BLOCKED", "API_KEY_IP_ADDRESS_BLOCKED",
                            "API_KEY_SERVICE_BLOCKED", "API_KEY_INVALID",
                            "SERVICE_DISABLED", "BILLING_DISABLED", "CONSUMER_SUSPENDED" -> reason;
                    default -> "UNKNOWN";
                };
            }
        }
        return "UNKNOWN";
    }

    private static String googleErrorMessage(String reason) {
        return switch (reason) {
            case "API_KEY_ANDROID_APP_BLOCKED", "API_KEY_IOS_APP_BLOCKED", "API_KEY_HTTP_REFERRER_BLOCKED" ->
                    "The backend Places key has a client-app restriction. Use a separate server-side key.";
            case "API_KEY_IP_ADDRESS_BLOCKED" ->
                    "Google rejected the backend server's IP address. Check the Places key's IP restrictions.";
            case "API_KEY_SERVICE_BLOCKED", "SERVICE_DISABLED" ->
                    "The backend key cannot use Places API (New). Check its API restrictions and Cloud project.";
            case "API_KEY_INVALID" -> "The backend Places API key is invalid.";
            case "BILLING_DISABLED", "CONSUMER_SUSPENDED" ->
                    "Google rejected the request due to the Cloud project's billing or account status.";
            default -> "Nearby care is unavailable. Check the Google Places API (New) key, restrictions and billing.";
        };
    }

    private static List<NearbyCareResponse> mapPlaces(GooglePlacesResponse response) {
        List<NearbyCareResponse> results = new ArrayList<>();
        if (response == null || response.places() == null) {
            return results;
        }

        for (GooglePlace place : response.places()) {
            if (place.location() == null) {
                continue;
            }
            results.add(new NearbyCareResponse(
                    place.id(),
                    place.displayName() == null ? "Care provider" : place.displayName().text(),
                    place.formattedAddress() == null ? "Address unavailable" : place.formattedAddress(),
                    place.nationalPhoneNumber(),
                    place.location().latitude(),
                    place.location().longitude(),
                    place.googleMapsUri(),
                    place.rating()));
        }
        return results;
    }

    private record GooglePlacesResponse(List<GooglePlace> places) {}

    private record GooglePlace(String id, GoogleDisplayName displayName, String formattedAddress,
            String nationalPhoneNumber, GoogleLocation location, String googleMapsUri, Double rating) {}

    private record GoogleDisplayName(String text) {}

    private record GoogleLocation(double latitude, double longitude) {}
}
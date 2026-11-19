package fi.haagahelia.smarthhealth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class NearbyCareServiceTest {

    @Test
    void searchesForPharmaciesWithinFiveKilometres() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://places.example.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://places.example.test/places:searchNearby"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"includedTypes":["pharmacy"],"maxResultCount":20,
                         "locationRestriction":{"circle":{"center":{"latitude":60.17,"longitude":24.94},"radius":5000}}}
                        """))
                .andRespond(withSuccess("""
                        {"places":[{"id":"p1","displayName":{"text":"Example Pharmacy"},
                        "formattedAddress":"Example Street","location":{"latitude":60.17,"longitude":24.94}}]}
                        """, MediaType.APPLICATION_JSON));

        var results = new NearbyCareService("example-test-key", builder.build())
                .search(60.17, 24.94, "pharmacy");

        assertEquals(1, results.size());
        assertEquals("Example Pharmacy", results.get(0).name());
        server.verify();
    }

    @Test
    void explainsAndroidRestrictedKeyWithoutExposingProviderDetails() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://places.example.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://places.example.test/places:searchNearby"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"message":"private provider detail","details":[
                                {"reason":"API_KEY_ANDROID_APP_BLOCKED"}]}}
                                """));

        var exception = assertThrows(ResponseStatusException.class,
                () -> new NearbyCareService("example-test-key", builder.build())
                        .search(60.17, 24.94, "hospital"));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertEquals("The backend Places key has a client-app restriction. Use a separate server-side key.",
                exception.getReason());
        server.verify();
    }

    @Test
    void givesGenericAdviceWhenGoogleProvidesNoKnownReason() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://places.example.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://places.example.test/places:searchNearby"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"private provider detail\"}}"));

        var exception = assertThrows(ResponseStatusException.class,
                () -> new NearbyCareService("example-test-key", builder.build())
                        .search(60.17, 24.94, "hospital"));

        assertEquals("Nearby care is unavailable. Check the Google Places API (New) key, restrictions and billing.",
                exception.getReason());
        server.verify();
    }
}

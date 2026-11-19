package fi.haagahelia.smarthhealth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import fi.haagahelia.smarthhealth.integration.nhs.NhsApiClient;

class NhsServiceTest {

    @Test
    void ranksConditionsReturnedForMoreSymptomSearchesFirst() {
        NhsApiClient client = mock(NhsApiClient.class);
        Map<String, Object> commonCondition = Map.of(
                "name", "Example condition",
                "url", "https://www.nhs.uk/example-condition",
            "causes", "An example cause",
            "symptoms", "Cough and fever",
            "treatment", "An example treatment");
        Map<String, Object> coughOnlyCondition = Map.of(
                "name", "Another condition",
                "url", "https://www.nhs.uk/another-condition",
                "symptoms", "Cough");

        when(client.searchContent("cough")).thenReturn(List.of(commonCondition, coughOnlyCondition));
        when(client.searchContent("fever")).thenReturn(List.of(commonCondition));

        var response = new NhsService(client).searchBySymptoms(List.of("cough", "fever"));

        assertEquals(2, response.possibilities().size());
        assertEquals("Example condition", response.possibilities().get(0).name());
        assertEquals("An example cause", response.possibilities().get(0).causes());
        assertEquals("Cough and fever", response.possibilities().get(0).symptoms());
        assertEquals("An example treatment", response.possibilities().get(0).treatment());
        assertEquals("https://www.nhs.uk/example-condition", response.possibilities().get(0).sourceUrl());
        assertFalse(response.disclaimer().isBlank());
    }

    @Test
    void buildsNhsConditionPageUrlFromNameWithoutCallingApi() {
        NhsApiClient client = mock(NhsApiClient.class);

        var result = new NhsService(client).searchByName("  Flu  ");

        assertEquals("Flu", result.name());
        assertEquals("", result.causes());
        assertEquals("", result.symptoms());
        assertEquals("", result.treatment());
        assertEquals("https://www.nhs.uk/conditions/flu/#maincontent", result.sourceUrl());
        verifyNoInteractions(client);
    }

    @Test
    void normalizesMultiWordConditionNamesIntoNhsSlugs() {
        NhsApiClient client = mock(NhsApiClient.class);

        var result = new NhsService(client).searchByName("Type 2 diabetes");

        assertEquals("https://www.nhs.uk/conditions/type-2-diabetes/#maincontent",
                result.sourceUrl());
    }
}

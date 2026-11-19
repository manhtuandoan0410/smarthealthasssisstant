package fi.haagahelia.smarthhealth.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import fi.haagahelia.smarthhealth.domain.AppUser;
import fi.haagahelia.smarthhealth.repository.AppUserRepository;
import fi.haagahelia.smarthhealth.service.NearbyCareService;
import fi.haagahelia.smarthhealth.service.NhsService;
import fi.haagahelia.smarthhealth.web.dto.ConditionResponse;
import fi.haagahelia.smarthhealth.web.dto.NearbyCareResponse;
import fi.haagahelia.smarthhealth.web.dto.SymptomSearchResponse;

@SpringBootTest(properties = {
        "app.mail.from=test-sender@example.test",
        "APP_ADMIN_EMAIL=admin@example.test",
        "APP_ADMIN_PASSWORD=example-test-password"
})
@AutoConfigureMockMvc
class SearchAndAuthIntegrationTest {

    private static final String PASSWORD = "test-password-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private NhsService nhsService;

    @MockitoBean
    private NearbyCareService nearbyCareService;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void conditionSearchIsPublicAndReturnsConditionResultFields() throws Exception {
        when(nhsService.searchByName("asthma")).thenReturn(
                new ConditionResponse("Asthma", "", "", "",
                        "https://www.nhs.uk/conditions/asthma/#maincontent"));

        mockMvc.perform(get("/api/conditions").param("query", "asthma"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Asthma"))
                .andExpect(jsonPath("$.causes").value(""))
                .andExpect(jsonPath("$.symptoms").value(""))
                .andExpect(jsonPath("$.treatment").value(""))
                .andExpect(jsonPath("$.sourceUrl")
                        .value("https://www.nhs.uk/conditions/asthma/#maincontent"));
    }

    @Test
    void nearbyCareSearchIsPublicAndReturnsMapMarkerFields() throws Exception {
        when(nearbyCareService.search(60.17, 24.94, "hospital")).thenReturn(List.of(
                new NearbyCareResponse("place-1", "Example Hospital", "Example Street", "0101234567",
                        60.18, 24.95, "https://maps.example.test/place-1", 4.2)));

        mockMvc.perform(get("/api/nearby-care")
                        .param("latitude", "60.17")
                        .param("longitude", "24.94")
                        .param("category", "hospital"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Example Hospital"))
                .andExpect(jsonPath("$[0].latitude").value(60.18))
                .andExpect(jsonPath("$[0].longitude").value(24.95))
                .andExpect(jsonPath("$[0].phoneNumber").value("0101234567"));
    }

    @Test
    void configuredAdminCanLogInAndUseAuthenticatedRoutes() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.test\",\"password\":\"example-test-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();
        Matcher matcher = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"")
                .matcher(result.getResponse().getContentAsString());
        assertTrue(matcher.find());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + matcher.group(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("admin@example.test"));
    }

    @Test
    void pharmacySearchIsPublic() throws Exception {
        when(nearbyCareService.search(60.17, 24.94, "pharmacy")).thenReturn(List.of(
                new NearbyCareResponse("pharmacy-1", "Example Pharmacy", "Example Street", null,
                        60.17, 24.94, null, null)));

        mockMvc.perform(get("/api/nearby-care")
                        .param("latitude", "60.17")
                        .param("longitude", "24.94")
                        .param("category", "pharmacy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Example Pharmacy"));
    }

    @Test
    void symptomSearchRequiresLoginAndAtLeastTwoSymptoms() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/symptoms/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symptoms\":[\"cough\",\"fever\"]}"))
                .andExpect(status().isUnauthorized());

        String email = uniqueEmail();
        String token = signup(email);
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(email));
        when(nhsService.searchBySymptoms(List.of("cough", "fever"))).thenReturn(
                new SymptomSearchResponse("Not a diagnosis", List.of(
                        new ConditionResponse("Example condition", "Cause", "Cough and fever", "Care", null))));

        mockMvc.perform(post("/api/symptoms/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symptoms\":[\"cough\"]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/symptoms/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symptoms\":[\"cough\",\"fever\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.possibilities[0].name").value("Example condition"))
                .andExpect(jsonPath("$.disclaimer").value("Not a diagnosis"));
    }

    @Test
    void logoutInvalidatesSessionForSymptomsAndCurrentUser() throws Exception {
        String token = signup(uniqueEmail());

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/symptoms/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symptoms\":[\"cough\",\"fever\"]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signupHashesPasswordAndPasswordResetInvalidatesOldSession() throws Exception {
        String email = uniqueEmail();
        String oldToken = signup(email);
        AppUser account = users.findByEmailIgnoreCase(email).orElseThrow();
        assertFalse(account.getPasswordHash().equals(PASSWORD));
        assertTrue(passwordEncoder.matches(PASSWORD, account.getPasswordHash()));

        org.mockito.ArgumentCaptor<SimpleMailMessage> mailCaptor =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "If an account exists for that email, reset instructions will be sent."));
        verify(mailSender).send(mailCaptor.capture());

        String resetCode = extractResetCode(mailCaptor.getValue().getText());
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"token\":\"" + resetCode
                                + "\",\"newPassword\":\"replacement-password-456\"}"))
                .andExpect(status().isOk());

        assertTrue(passwordEncoder.matches("replacement-password-456",
                users.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash()));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"replacement-password-456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        mockMvc.perform(post("/api/symptoms/search")
                        .header("Authorization", "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symptoms\":[\"cough\",\"fever\"]}"))
                .andExpect(status().isUnauthorized());
    }

    private String signup(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email
                                + "\",\"password\":\"" + PASSWORD
                                + "\",\"firstName\":\"Test\",\"lastName\":\"User\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        Matcher matcher = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"")
                .matcher(result.getResponse().getContentAsString());
        if (!matcher.find()) {
            throw new AssertionError("Signup response did not contain an access token.");
        }
        return matcher.group(1);
    }

    private static String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@example.test";
    }

    private static String extractResetCode(String message) {
        Matcher matcher = Pattern.compile("Your password reset code is:\\s*([A-Za-z0-9_-]+)")
                .matcher(message);
        if (!matcher.find()) {
            throw new AssertionError("Reset email did not contain a reset code.");
        }
        return matcher.group(1);
    }
}

package fi.haagahelia.smarthhealth.web.dto;

import java.time.Instant;

public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, String email, String role) {}
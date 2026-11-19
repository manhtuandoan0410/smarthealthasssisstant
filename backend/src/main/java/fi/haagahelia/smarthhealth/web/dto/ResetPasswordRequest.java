package fi.haagahelia.smarthhealth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Email String email,
        @NotBlank String token,
        @NotBlank @Size(min = 12, max = 72) String newPassword) {}
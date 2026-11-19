package fi.haagahelia.smarthhealth.config;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import fi.haagahelia.smarthhealth.domain.AppUser;
import fi.haagahelia.smarthhealth.repository.AppUserRepository;
import fi.haagahelia.smarthhealth.web.dto.LoginRequest;
import jakarta.validation.Validator;

@Component
public class AdminAccountInitializer implements ApplicationRunner {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;
    private final String email;
    private final String password;

    public AdminAccountInitializer(AppUserRepository users, PasswordEncoder passwordEncoder, Validator validator,
            @Value("${app.admin.email:}") String email,
            @Value("${app.admin.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.isBlank()
                || !validator.validateValue(LoginRequest.class, "email", email).isEmpty()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Configure a valid APP_ADMIN_EMAIL and a non-blank APP_ADMIN_PASSWORD of at most 72 UTF-8 bytes.");
        }

        var existing = users.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            if (existing.get().getRole() != AppUser.Role.ADMIN) {
                throw new IllegalStateException("The configured admin email is already used by a non-admin account.");
            }
            return;
        }

        AppUser admin = new AppUser();
        admin.setEmail(email);
        admin.setUsername(email);
        admin.setFirstName("Admin");
        admin.setLastName("Account");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(AppUser.Role.ADMIN);
        users.save(admin);
    }
}

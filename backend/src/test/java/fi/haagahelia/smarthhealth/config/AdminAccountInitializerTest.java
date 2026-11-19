package fi.haagahelia.smarthhealth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import fi.haagahelia.smarthhealth.domain.AppUser;
import fi.haagahelia.smarthhealth.repository.AppUserRepository;
import jakarta.validation.Validation;

class AdminAccountInitializerTest {

    @Test
    void createsAdminWithHashedConfiguredPassword() {
        AppUserRepository users = mock(AppUserRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        var initializer = new AdminAccountInitializer(users, encoder,
                Validation.buildDefaultValidatorFactory().getValidator(), "ADMIN@example.test", "example-test-password");

        initializer.run(null);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(captor.capture());
        assertEquals("admin@example.test", captor.getValue().getEmail());
        assertEquals(AppUser.Role.ADMIN, captor.getValue().getRole());
        assertTrue(encoder.matches("example-test-password", captor.getValue().getPasswordHash()));
    }

    @Test
    void neverPromotesAnExistingUserWithTheSameEmail() {
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findByEmailIgnoreCase("admin@example.test")).thenReturn(Optional.of(new AppUser()));
        var initializer = new AdminAccountInitializer(users, new BCryptPasswordEncoder(4),
                Validation.buildDefaultValidatorFactory().getValidator(), "admin@example.test", "example-test-password");

        assertThrows(IllegalStateException.class, () -> initializer.run(null));
        verify(users, never()).save(any());
    }
}

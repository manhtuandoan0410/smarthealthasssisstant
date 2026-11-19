package fi.haagahelia.smarthhealth.service;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import fi.haagahelia.smarthhealth.domain.AppUser;
import fi.haagahelia.smarthhealth.domain.AuthSession;
import fi.haagahelia.smarthhealth.domain.PasswordResetToken;
import fi.haagahelia.smarthhealth.repository.AppUserRepository;
import fi.haagahelia.smarthhealth.repository.AuthSessionRepository;
import fi.haagahelia.smarthhealth.repository.PasswordResetTokenRepository;
import fi.haagahelia.smarthhealth.web.dto.LoginRequest;
import fi.haagahelia.smarthhealth.web.dto.ResetPasswordRequest;
import fi.haagahelia.smarthhealth.web.dto.SignupRequest;

class AuthServiceTest {

    private AppUserRepository users;
    private AuthSessionRepository sessions;
    private PasswordResetTokenRepository resetTokens;
    private BCryptPasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        sessions = mock(AuthSessionRepository.class);
        resetTokens = mock(PasswordResetTokenRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        authService = new AuthService(users, sessions, resetTokens, passwordEncoder,
                mock(JavaMailSender.class), "no-reply@example.test");
        when(users.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessions.save(any(AuthSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void signupStoresBcryptHashAndOnlyHashOfSessionToken() {
        when(users.existsByEmailIgnoreCase("person@example.test")).thenReturn(false);

        var response = authService.signup(new SignupRequest(
                " Person@Example.Test ", "long-enough-password", "Pat", "Example"));

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(userCaptor.capture());
        String passwordHash = userCaptor.getValue().getPasswordHash();
        assertNotEquals("long-enough-password", passwordHash);
        assertTrue(passwordEncoder.matches("long-enough-password", passwordHash));
        assertEquals("person@example.test", userCaptor.getValue().getEmail());

        ArgumentCaptor<AuthSession> sessionCaptor = ArgumentCaptor.forClass(AuthSession.class);
        verify(sessions).save(sessionCaptor.capture());
        assertNotEquals(response.accessToken(), sessionCaptor.getValue().getTokenHash());
        assertNotNull(response.expiresAt());
    }

    @Test
    void loginAcceptsMatchingPasswordAndRejectsIncorrectPassword() {
        AppUser user = user("person@example.test", passwordEncoder.encode("long-enough-password"));
        when(users.findByEmailIgnoreCase("person@example.test")).thenReturn(Optional.of(user));

        var response = authService.login(new LoginRequest("PERSON@example.test", "long-enough-password"));
        assertNotNull(response.accessToken());

        assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequest("person@example.test", "wrong-password")));
        verify(sessions).save(any(AuthSession.class));
    }

    @Test
    void resetPasswordReplacesPasswordAndInvalidatesTokensAndSessions() {
        AppUser user = user("person@example.test", passwordEncoder.encode("old-long-password"));
        user.setId(42L);
        String rawResetToken = TokenUtil.generateToken();
        PasswordResetToken resetToken = new PasswordResetToken(
                user, TokenUtil.sha256(rawResetToken), Instant.now().plusSeconds(600));
        when(resetTokens.findFirstByTokenHashAndExpiresAtAfter(eq(TokenUtil.sha256(rawResetToken)), any(Instant.class)))
                .thenReturn(Optional.of(resetToken));

        authService.resetPassword(new ResetPasswordRequest(
                "person@example.test", rawResetToken, "new-long-password"));

        assertTrue(passwordEncoder.matches("new-long-password", user.getPasswordHash()));
        assertTrue(!passwordEncoder.matches("old-long-password", user.getPasswordHash()));
        verify(resetTokens).deleteAllByUser_Id(42L);
        verify(sessions).deleteAllByUser_Id(42L);
    }

    @Test
    void unknownEmailDoesNotCreateOrSendPasswordReset() {
        when(users.findByEmailIgnoreCase("unknown@example.test")).thenReturn(Optional.empty());

        authService.requestPasswordReset("unknown@example.test");

        verify(resetTokens, never()).save(any(PasswordResetToken.class));
        verify(resetTokens, never()).deleteAllByUser_Id(any(Long.class));
    }

    private static AppUser user(String email, String passwordHash) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setUsername(email);
        user.setPasswordHash(passwordHash);
        return user;
    }
}

package fi.haagahelia.smarthhealth.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import fi.haagahelia.smarthhealth.domain.AppUser;
import fi.haagahelia.smarthhealth.domain.AuthSession;
import fi.haagahelia.smarthhealth.domain.PasswordResetToken;
import fi.haagahelia.smarthhealth.repository.AppUserRepository;
import fi.haagahelia.smarthhealth.repository.AuthSessionRepository;
import fi.haagahelia.smarthhealth.repository.PasswordResetTokenRepository;
import fi.haagahelia.smarthhealth.web.dto.AuthResponse;
import fi.haagahelia.smarthhealth.web.dto.LoginRequest;
import fi.haagahelia.smarthhealth.web.dto.ResetPasswordRequest;
import fi.haagahelia.smarthhealth.web.dto.SignupRequest;

@Service
public class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private static final Duration SESSION_LIFETIME = Duration.ofDays(30);
    private static final Duration RESET_TOKEN_LIFETIME = Duration.ofMinutes(15);

    private final AppUserRepository users;
    private final AuthSessionRepository sessions;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final String mailFrom;

    public AuthService(AppUserRepository users, AuthSessionRepository sessions,
            PasswordResetTokenRepository resetTokens, PasswordEncoder passwordEncoder,
            JavaMailSender mailSender, @Value("${app.mail.from:}") String mailFrom) {
        this.users = users;
        this.sessions = sessions;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        validatePasswordBytes(request.password());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with that email already exists.");
        }

        AppUser user = new AppUser();
        user.setUsername(email);
        user.setEmail(email);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return createSession(users.save(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        AppUser user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }
        return createSession(user);
    }

    @Transactional
    public void requestPasswordReset(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        users.findByEmailIgnoreCase(email).ifPresent(user -> {
            resetTokens.deleteAllByUser_Id(user.getId());
            String rawToken = TokenUtil.generateToken();
            resetTokens.save(new PasswordResetToken(user, TokenUtil.sha256(rawToken),
                    Instant.now().plus(RESET_TOKEN_LIFETIME)));
            try {
                if (mailFrom == null || mailFrom.isBlank()) {
                    throw new IllegalStateException("MAIL_FROM is not configured");
                }
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(mailFrom);
                message.setTo(email);
                message.setSubject("Smart Health Assistant password reset");
                message.setText("Your password reset code is:\n\n" + rawToken
                        + "\n\nIt expires in 15 minutes. If you did not request this, ignore this email.");
                mailSender.send(message);
            } catch (MailException | IllegalStateException exception) {
                LOGGER.warn("Password reset email could not be sent; check SMTP configuration.");
            }
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        validatePasswordBytes(request.newPassword());
        String email = normalizeEmail(request.email());
        PasswordResetToken resetToken = resetTokens
                .findFirstByTokenHashAndExpiresAtAfter(TokenUtil.sha256(request.token()), Instant.now())
                .filter(token -> normalizeEmail(token.getUser().getEmail()).equals(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset code is invalid or expired."));
        AppUser user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        users.save(user);
        resetTokens.deleteAllByUser_Id(user.getId());
        sessions.deleteAllByUser_Id(user.getId());
    }

    @Transactional
    public void logout(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            sessions.deleteByTokenHash(TokenUtil.sha256(authorization.substring(7).trim()));
        }
    }

    private AuthResponse createSession(AppUser user) {
        String rawToken = TokenUtil.generateToken();
        Instant expiresAt = Instant.now().plus(SESSION_LIFETIME);
        sessions.save(new AuthSession(user, TokenUtil.sha256(rawToken), expiresAt));
        return new AuthResponse(rawToken, "Bearer", expiresAt, user.getEmail(), user.getRole().name());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static void validatePasswordBytes(String password) {
        int byteLength = password.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength < 12 || byteLength > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 12 characters and no more than 72 UTF-8 bytes.");
        }
    }
}
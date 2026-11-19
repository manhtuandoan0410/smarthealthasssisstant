package fi.haagahelia.smarthhealth.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import fi.haagahelia.smarthhealth.domain.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findFirstByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);

    void deleteAllByUser_Id(Long userId);
}
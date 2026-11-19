package fi.haagahelia.smarthhealth.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import fi.haagahelia.smarthhealth.domain.AuthSession;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    Optional<AuthSession> findFirstByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);

    void deleteAllByUser_Id(Long userId);

    void deleteByTokenHash(String tokenHash);
}
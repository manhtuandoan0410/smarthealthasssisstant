package fi.haagahelia.smarthhealth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import fi.haagahelia.smarthhealth.domain.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
	Optional<AppUser> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);
}

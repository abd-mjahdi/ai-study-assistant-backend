package ma.ensa.aistudyassistant.repository;

import ma.ensa.aistudyassistant.model.entities.StudySession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudySessionRepository extends JpaRepository<StudySession, UUID> {
    List<StudySession> findAllByUserEmailOrderByCreatedAtDesc(String email);
    Optional<StudySession> findByIdAndUserEmail(UUID id, String email);
}

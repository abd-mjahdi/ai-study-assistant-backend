package ma.ensa.aistudyassistant.repository;

import ma.ensa.aistudyassistant.model.entities.StudySession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StudySessionRepository extends JpaRepository<StudySession, UUID> {
}

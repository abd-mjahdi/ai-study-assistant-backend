package ma.ensa.aistudyassistant.history.dto;

import ma.ensa.aistudyassistant.model.enums.StudyType;

import java.time.LocalDateTime;
import java.util.UUID;

public record HistoryItemResponse(
        UUID sessionId,
        StudyType type,
        String subject,
        LocalDateTime createdAt
) {
}

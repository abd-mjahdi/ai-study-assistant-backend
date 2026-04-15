package ma.ensa.aistudyassistant.study.dto;

import ma.ensa.aistudyassistant.model.enums.StudyType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record GenerateStudyResponse(
        UUID sessionId,
        StudyType type,
        String subject,
        String summary,
        List<QuizQuestionResponse> questions,
        List<FlashcardResponse> flashcards,
        LocalDateTime createdAt
) {
}

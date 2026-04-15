package ma.ensa.aistudyassistant.study.dto;

import java.util.List;

public record QuizQuestionResponse(
        String question,
        List<String> choices,
        Integer correctIndex,
        String explanation
) {
}

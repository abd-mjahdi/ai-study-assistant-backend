package ma.ensa.aistudyassistant.study.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ma.ensa.aistudyassistant.model.enums.StudyType;

public record GenerateStudyRequest(
        @NotBlank
        @Size(min = 20, max = 20000)
        String text,

        @NotNull
        StudyType type,

        @Size(max = 120)
        String subject
) {
}

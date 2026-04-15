package ma.ensa.aistudyassistant.study;

import jakarta.validation.Valid;
import ma.ensa.aistudyassistant.study.dto.GenerateStudyRequest;
import ma.ensa.aistudyassistant.study.dto.GenerateStudyResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/study")
public class StudyController {

    private final StudyService studyService;

    public StudyController(StudyService studyService) {
        this.studyService = studyService;
    }

    @PostMapping("/generate")
    public GenerateStudyResponse generate(
            @Valid @RequestBody GenerateStudyRequest request,
            Authentication authentication
    ) {
        return studyService.generate(authentication.getName(), request);
    }
}

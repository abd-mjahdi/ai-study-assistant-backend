package ma.ensa.aistudyassistant.history;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.ensa.aistudyassistant.history.dto.HistoryItemResponse;
import ma.ensa.aistudyassistant.model.entities.Question;
import ma.ensa.aistudyassistant.model.entities.StudySession;
import ma.ensa.aistudyassistant.repository.StudySessionRepository;
import ma.ensa.aistudyassistant.study.dto.FlashcardResponse;
import ma.ensa.aistudyassistant.study.dto.GenerateStudyResponse;
import ma.ensa.aistudyassistant.study.dto.QuizQuestionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class HistoryService {

    private final StudySessionRepository studySessionRepository;
    private final ObjectMapper objectMapper;

    public HistoryService(StudySessionRepository studySessionRepository, ObjectMapper objectMapper) {
        this.studySessionRepository = studySessionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<HistoryItemResponse> list(String userEmail) {
        return studySessionRepository.findAllByUserEmailOrderByCreatedAtDesc(userEmail).stream()
                .map(s -> new HistoryItemResponse(s.getId(), s.getType(), s.getSubject(), s.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public GenerateStudyResponse getOne(String userEmail, UUID sessionId) {
        StudySession session = studySessionRepository.findByIdAndUserEmail(sessionId, userEmail)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Session not found"));

        return switch (session.getType()) {
            case SUMMARY -> new GenerateStudyResponse(
                    session.getId(),
                    session.getType(),
                    session.getSubject(),
                    session.getSummaryContent(),
                    null,
                    null,
                    session.getCreatedAt()
            );
            case QUIZ -> new GenerateStudyResponse(
                    session.getId(),
                    session.getType(),
                    session.getSubject(),
                    null,
                    mapQuestions(session.getQuestions()),
                    null,
                    session.getCreatedAt()
            );
            case FLASHCARDS -> new GenerateStudyResponse(
                    session.getId(),
                    session.getType(),
                    session.getSubject(),
                    null,
                    null,
                    session.getFlashcards().stream()
                            .sorted((a, b) -> Integer.compare(
                                    a.getOrderIndex() == null ? 0 : a.getOrderIndex(),
                                    b.getOrderIndex() == null ? 0 : b.getOrderIndex()
                            ))
                            .map(f -> new FlashcardResponse(f.getFront(), f.getBack()))
                            .toList(),
                    session.getCreatedAt()
            );
        };
    }

    @Transactional
    public void delete(String userEmail, UUID sessionId) {
        StudySession session = studySessionRepository.findByIdAndUserEmail(sessionId, userEmail)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Session not found"));
        studySessionRepository.delete(session);
    }

    private List<QuizQuestionResponse> mapQuestions(List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            return List.of();
        }

        List<Question> sorted = new ArrayList<>(questions);
        sorted.sort((a, b) -> Integer.compare(
                a.getOrderIndex() == null ? 0 : a.getOrderIndex(),
                b.getOrderIndex() == null ? 0 : b.getOrderIndex()
        ));

        List<QuizQuestionResponse> result = new ArrayList<>();
        for (Question q : sorted) {
            List<String> choices = parseChoices(q.getChoices());
            int correctIndex = choices.indexOf(q.getCorrectAnswer());
            if (correctIndex < 0) {
                throw new ResponseStatusException(BAD_GATEWAY, "Stored quiz data is invalid");
            }

            result.add(new QuizQuestionResponse(
                    q.getQuestionText(),
                    choices,
                    correctIndex,
                    q.getExplanation()
            ));
        }
        return result;
    }

    private List<String> parseChoices(String rawJsonArray) {
        if (rawJsonArray == null || rawJsonArray.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> parsed = objectMapper.readValue(rawJsonArray, new TypeReference<>() {
            });
            return parsed == null ? Collections.emptyList() : parsed;
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Stored quiz data is malformed");
        }
    }
}


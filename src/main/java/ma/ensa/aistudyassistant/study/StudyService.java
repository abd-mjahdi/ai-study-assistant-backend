package ma.ensa.aistudyassistant.study;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.ensa.aistudyassistant.model.entities.Flashcard;
import ma.ensa.aistudyassistant.model.entities.Question;
import ma.ensa.aistudyassistant.model.entities.StudySession;
import ma.ensa.aistudyassistant.model.entities.User;
import ma.ensa.aistudyassistant.repository.StudySessionRepository;
import ma.ensa.aistudyassistant.repository.UserRepository;
import ma.ensa.aistudyassistant.study.dto.FlashcardResponse;
import ma.ensa.aistudyassistant.study.dto.GenerateStudyRequest;
import ma.ensa.aistudyassistant.study.dto.GenerateStudyResponse;
import ma.ensa.aistudyassistant.study.dto.QuizQuestionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class StudyService {

    private static final Logger log = LoggerFactory.getLogger(StudyService.class);

    private static final int MIN_INPUT_TEXT_LENGTH = 20;
    private static final int MAX_INPUT_TEXT_LENGTH = 20_000;
    private static final int MAX_SUBJECT_LENGTH = 120;
    private static final int MAX_DB_CORRECT_ANSWER_LENGTH = 255;
    private static final int REQUIRED_CHOICES_COUNT = 4;
    private static final int QUIZ_QUESTION_TARGET = 5;
    private static final int FLASHCARD_TARGET = 8;
    private static final int MAX_ACCEPTED_QUIZ_QUESTIONS = 12;
    private static final int MAX_ACCEPTED_FLASHCARDS = 20;

    private final UserRepository userRepository;
    private final StudySessionRepository studySessionRepository;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public StudyService(
            UserRepository userRepository,
            StudySessionRepository studySessionRepository,
            GeminiClient geminiClient,
            ObjectMapper objectMapper
    ) {
        this.userRepository = userRepository;
        this.studySessionRepository = studySessionRepository;
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public GenerateStudyResponse generate(String userEmail, GenerateStudyRequest request) {
        validateGenerateRequest(userEmail, request);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));

        String subject = normalizeSubject(request.subject());
        String inputText = request.text().trim();

        log.info("study.generate.start userId={} type={} subjectPresent={}",
                user.getId(), request.type(), subject != null);

        StudySession session = StudySession.builder()
                .user(user)
                .type(request.type())
                .subject(subject)
                .build();

        PreparedGeneration generated = switch (request.type()) {
            case SUMMARY -> prepareSummary(session, inputText);
            case QUIZ -> prepareQuiz(session, inputText);
            case FLASHCARDS -> prepareFlashcards(session, inputText);
        };

        StudySession saved = studySessionRepository.save(session);

        log.info("study.generate.success userId={} sessionId={} type={}",
                user.getId(), saved.getId(), saved.getType());

        return new GenerateStudyResponse(
                saved.getId(),
                saved.getType(),
                saved.getSubject(),
                generated.summary(),
                generated.questions(),
                generated.flashcards(),
                saved.getCreatedAt()
        );
    }

    private PreparedGeneration prepareSummary(StudySession session, String text) {
        String prompt = """
                You are an assistant generating a concise educational summary.
                Return ONLY valid JSON with this exact shape:
                {
                  "summary": "string"
                }
                Rules:
                - Summary should be clear and in plain text.
                - No markdown, no backticks, no extra keys.
                """;

        JsonNode json = geminiClient.generateJson(prompt, text);
        String summary = readRequiredText(json, "summary");
        session.setSummaryContent(summary);

        return new PreparedGeneration(summary, null, null);
    }

    private PreparedGeneration prepareQuiz(StudySession session, String text) {
        String prompt = """
                You are an assistant generating multiple-choice quiz questions.
                Return ONLY valid JSON with this exact shape:
                {
                  "questions": [
                    {
                      "question": "string",
                      "choices": ["string","string","string","string"],
                      "correctIndex": 0,
                      "explanation": "string"
                    }
                  ]
                }
                Rules:
                - Provide exactly %d questions.
                - Each question must have exactly %d choices.
                - correctIndex must be between 0 and %d.
                - No markdown, no backticks, no extra keys.
                """.formatted(QUIZ_QUESTION_TARGET, REQUIRED_CHOICES_COUNT, REQUIRED_CHOICES_COUNT - 1);

        JsonNode json = geminiClient.generateJson(prompt, text);
        JsonNode questionsNode = json.get("questions");
        if (questionsNode == null || !questionsNode.isArray() || questionsNode.isEmpty()) {
            throw new ResponseStatusException(BAD_GATEWAY, "Gemini returned no quiz questions");
        }
        if (questionsNode.size() > MAX_ACCEPTED_QUIZ_QUESTIONS) {
            throw new ResponseStatusException(BAD_GATEWAY, "Gemini returned too many quiz questions");
        }

        List<QuizQuestionResponse> responseQuestions = new ArrayList<>();
        int order = 0;
        for (JsonNode questionNode : questionsNode) {
            String questionText = readRequiredText(questionNode, "question");
            List<String> choices = readChoices(questionNode.get("choices"));
            int correctIndex = readCorrectIndex(questionNode.get("correctIndex"), choices.size());
            String explanation = readOptionalText(questionNode, "explanation");
            String correctAnswer = limitSize(choices.get(correctIndex), MAX_DB_CORRECT_ANSWER_LENGTH, "correctAnswer");

            Question question = Question.builder()
                    .questionText(questionText)
                    .choices(writeJsonArray(choices))
                    .correctAnswer(correctAnswer)
                    .explanation(explanation)
                    .orderIndex(order)
                    .build();
            session.addQuestion(question);

            responseQuestions.add(new QuizQuestionResponse(questionText, choices, correctIndex, explanation));
            order++;
        }

        return new PreparedGeneration(null, responseQuestions, null);
    }

    private PreparedGeneration prepareFlashcards(StudySession session, String text) {
        String prompt = """
                You are an assistant generating study flashcards.
                Return ONLY valid JSON with this exact shape:
                {
                  "flashcards": [
                    {
                      "front": "string",
                      "back": "string"
                    }
                  ]
                }
                Rules:
                - Provide exactly %d flashcards.
                - Keep each front concise and each back clear.
                - No markdown, no backticks, no extra keys.
                """.formatted(FLASHCARD_TARGET);

        JsonNode json = geminiClient.generateJson(prompt, text);
        JsonNode flashcardsNode = json.get("flashcards");
        if (flashcardsNode == null || !flashcardsNode.isArray() || flashcardsNode.isEmpty()) {
            throw new ResponseStatusException(BAD_GATEWAY, "Gemini returned no flashcards");
        }
        if (flashcardsNode.size() > MAX_ACCEPTED_FLASHCARDS) {
            throw new ResponseStatusException(BAD_GATEWAY, "Gemini returned too many flashcards");
        }

        List<FlashcardResponse> responseFlashcards = new ArrayList<>();
        int order = 0;
        for (JsonNode flashcardNode : flashcardsNode) {
            String front = readRequiredText(flashcardNode, "front");
            String back = readRequiredText(flashcardNode, "back");

            Flashcard flashcard = Flashcard.builder()
                    .front(front)
                    .back(back)
                    .orderIndex(order)
                    .build();
            session.addFlashcard(flashcard);

            responseFlashcards.add(new FlashcardResponse(front, back));
            order++;
        }

        return new PreparedGeneration(null, null, responseFlashcards);
    }

    private String normalizeSubject(String subject) {
        if (subject == null) {
            return null;
        }
        String trimmed = subject.trim();
        if (trimmed.length() > MAX_SUBJECT_LENGTH) {
            throw new ResponseStatusException(BAD_REQUEST, "subject is too long");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String readRequiredText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isTextual() || value.asText().trim().isEmpty()) {
            throw new ResponseStatusException(BAD_GATEWAY, "Gemini response missing field: " + fieldName);
        }
        return value.asText().trim();
    }

    private String readOptionalText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new ResponseStatusException(BAD_GATEWAY, "Gemini response has invalid field: " + fieldName);
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private List<String> readChoices(JsonNode choicesNode) {
        if (choicesNode == null || !choicesNode.isArray() || choicesNode.size() != REQUIRED_CHOICES_COUNT) {
            throw new ResponseStatusException(BAD_GATEWAY, "Gemini response has invalid choices");
        }

        try {
            List<String> choices = objectMapper.convertValue(choicesNode, new TypeReference<>() {
            });
            boolean hasEmptyChoices = choices.stream().anyMatch(choice -> choice == null || choice.trim().isEmpty());
            if (hasEmptyChoices) {
                throw new ResponseStatusException(BAD_GATEWAY, "Gemini response has empty choice values");
            }

            List<String> normalizedChoices = choices.stream().map(String::trim).toList();
            if (normalizedChoices.stream().distinct().count() != normalizedChoices.size()) {
                throw new ResponseStatusException(BAD_GATEWAY, "Gemini response has duplicate choices");
            }

            return normalizedChoices;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Gemini response choices are malformed");
        }
    }

    private int readCorrectIndex(JsonNode indexNode, int choicesCount) {
        if (indexNode == null || !indexNode.canConvertToInt()) {
            throw new ResponseStatusException(BAD_GATEWAY, "Gemini response missing correctIndex");
        }
        int index = indexNode.asInt();
        if (index < 0 || index >= choicesCount) {
            throw new ResponseStatusException(BAD_GATEWAY, "Gemini response has invalid correctIndex");
        }
        return index;
    }

    private String limitSize(String value, int maxLength, String fieldName) {
        String trimmed = Objects.requireNonNull(value).trim();
        if (trimmed.length() > maxLength) {
            throw new ResponseStatusException(BAD_GATEWAY, "Gemini response field too long: " + fieldName);
        }
        return trimmed;
    }

    private String writeJsonArray(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to serialize choices");
        }
    }

    private void validateGenerateRequest(String userEmail, GenerateStudyRequest request) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid authenticated user");
        }
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        if (request.type() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "type is required");
        }
        if (request.text() == null || request.text().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "text is required");
        }

        String trimmedText = request.text().trim();
        if (trimmedText.length() < MIN_INPUT_TEXT_LENGTH) {
            throw new ResponseStatusException(BAD_REQUEST, "text is too short");
        }
        if (trimmedText.length() > MAX_INPUT_TEXT_LENGTH) {
            throw new ResponseStatusException(BAD_REQUEST, "text is too long");
        }
    }

    private record PreparedGeneration(
            String summary,
            List<QuizQuestionResponse> questions,
            List<FlashcardResponse> flashcards
    ) {
    }
}

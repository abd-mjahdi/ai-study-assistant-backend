package ma.ensa.aistudyassistant.study;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Component
public class GeminiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiClient(
            ObjectMapper objectMapper,
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.model:gemini-1.5-flash}") String model
    ) {
        this.restClient = RestClient.builder().build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    public JsonNode generateJson(String prompt, String inputText) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Gemini API key is missing");
        }

        Map<String, Object> payload = Map.of(
                "contents", List.of(
                        Map.of(
                                "parts", List.of(
                                        Map.of("text", prompt + "\n\nUSER_INPUT:\n" + inputText)
                                )
                        )
                )
        );

        GeminiResponse response;
        try {
            response = restClient.post()
                    .uri("https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(GeminiResponse.class);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to call Gemini API");
        }

        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new ResponseStatusException(BAD_GATEWAY, "Gemini returned empty response");
        }

        String raw = response.candidates().get(0).content().parts().stream()
                .map(Part::text)
                .reduce("", (a, b) -> a + b);

        String cleaned = raw
                .replace("```json", "")
                .replace("```", "")
                .trim();

        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Gemini response format is invalid");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiResponse(List<Candidate> candidates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Candidate(Content content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Content(List<Part> parts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Part(String text) {
    }
}

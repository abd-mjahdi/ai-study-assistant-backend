package ma.ensa.aistudyassistant.study;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
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
            @Value("${GEMINI_API_KEY:}") String apiKey,
            @Value("${gemini.model:gemini-flash-latest}") String model
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

        String rawResponseBody;
        try {
            rawResponseBody = restClient.post()
                    .uri("https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            String snippet = safeSnippet(ex.getResponseBodyAsString(), 600);
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Gemini API error (" + ex.getStatusCode().value() + "): " + snippet
            );
        } catch (RestClientException ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to call Gemini API: " + ex.getMessage());
        }

        if (rawResponseBody == null || rawResponseBody.isBlank()) {
            throw new ResponseStatusException(BAD_GATEWAY, "Gemini returned empty response body");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawResponseBody);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Gemini returned invalid JSON response");
        }

        String raw = extractTextFromCandidates(root);
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(BAD_GATEWAY, "Gemini returned empty content");
        }

        String cleaned = raw
                .replace("```json", "")
                .replace("```", "")
                .trim();

        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Gemini response format is invalid (expected JSON only)");
        }
    }

    private String extractTextFromCandidates(JsonNode root) {
        JsonNode candidates = root.get("candidates");
        if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
            return null;
        }

        JsonNode content = candidates.get(0).path("content");
        JsonNode parts = content.path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            JsonNode textNode = part.get("text");
            if (textNode != null && !textNode.isNull()) {
                sb.append(textNode.asText());
            }
        }
        return sb.toString();
    }

    private String safeSnippet(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen) + "...";
    }
}

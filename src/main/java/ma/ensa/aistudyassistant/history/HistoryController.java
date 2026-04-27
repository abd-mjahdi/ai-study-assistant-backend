package ma.ensa.aistudyassistant.history;

import ma.ensa.aistudyassistant.history.dto.HistoryItemResponse;
import ma.ensa.aistudyassistant.study.dto.GenerateStudyResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public List<HistoryItemResponse> list(Authentication authentication) {
        return historyService.list(authentication.getName());
    }

    @GetMapping("/{sessionId}")
    public GenerateStudyResponse getOne(
            @PathVariable UUID sessionId,
            Authentication authentication
    ) {
        return historyService.getOne(authentication.getName(), sessionId);
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID sessionId,
            Authentication authentication
    ) {
        historyService.delete(authentication.getName(), sessionId);
    }
}


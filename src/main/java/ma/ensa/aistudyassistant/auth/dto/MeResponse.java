package ma.ensa.aistudyassistant.auth.dto;

import java.util.UUID;

public record MeResponse(
        UUID userId,
        String username,
        String email
) {
}


package ma.ensa.aistudyassistant.common;

public record ApiErrorResponse(
        int status,
        String message
) {
}

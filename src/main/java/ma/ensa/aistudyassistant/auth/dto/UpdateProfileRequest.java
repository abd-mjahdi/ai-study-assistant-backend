package ma.ensa.aistudyassistant.auth.dto;

/**
 * Partial profile update. Omitted or blank fields are left unchanged.
 * Current password is required when changing email or password.
 */
public record UpdateProfileRequest(
        String username,
        String email,
        String currentPassword,
        String newPassword
) {
}

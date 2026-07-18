package gdg.sharinglog.service;

import java.time.Instant;

public record IssuedInvitation(
        Long invitationId,
        Long groupId,
        String code,
        Instant createdAt,
        Instant expiresAt
) {
}

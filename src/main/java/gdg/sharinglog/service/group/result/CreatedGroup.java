package gdg.sharinglog.service.group.result;

import java.time.Instant;

import gdg.sharinglog.domain.GroupRole;

public record CreatedGroup(
        Long groupId,
        String name,
        Long membershipId,
        GroupRole role,
        Instant createdAt
) {
}

package gdg.sharinglog.service;

import java.time.Instant;

import gdg.sharinglog.domain.GroupRole;

public record AcceptedInvitation(
        Long groupId,
        String groupName,
        Long membershipId,
        GroupRole role,
        Instant joinedAt,
        boolean joinedNow
) {
}

package gdg.sharinglog.web;

import java.time.Instant;

import gdg.sharinglog.domain.GroupRole;
import gdg.sharinglog.service.CreatedGroup;

public record GroupResponse(
        Long groupId,
        String name,
        Long membershipId,
        GroupRole role,
        Instant createdAt
) {

    public static GroupResponse from(CreatedGroup group) {
        return new GroupResponse(
                group.groupId(),
                group.name(),
                group.membershipId(),
                group.role(),
                group.createdAt()
        );
    }
}

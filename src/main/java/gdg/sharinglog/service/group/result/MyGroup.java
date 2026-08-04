package gdg.sharinglog.service.group.result;

import gdg.sharinglog.domain.GroupRole;

public record MyGroup(
        String groupPublicId,
        String membershipPublicId,
        long membershipVersion,
        String groupName,
        GroupRole role
) {
}

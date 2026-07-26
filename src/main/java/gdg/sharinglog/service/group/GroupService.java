package gdg.sharinglog.service.group;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.service.group.result.CreatedGroup;
import gdg.sharinglog.service.user.AuthenticatedUserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupService {

    private static final int MAX_GROUP_NAME_LENGTH = 50;

    private final SharingGroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final AuthenticatedUserService authenticatedUserService;

    public GroupService(SharingGroupRepository groupRepository,
                        GroupMemberRepository groupMemberRepository,
                        AuthenticatedUserService authenticatedUserService) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.authenticatedUserService = authenticatedUserService;
    }

    @Transactional
    public CreatedGroup createGroup(String requestedName, String registrationId, OAuth2User oAuth2User) {
        String groupName = normalizeGroupName(requestedName);
        User creator = authenticatedUserService.requireUser(registrationId, oAuth2User);

        SharingGroup group = groupRepository.save(new SharingGroup(groupName, creator));
        GroupMember ownerMembership = groupMemberRepository.save(GroupMember.owner(group, creator));

        return new CreatedGroup(
                group.getId(),
                group.getName(),
                ownerMembership.getId(),
                ownerMembership.getRole(),
                group.getCreatedAt()
        );
    }

    private String normalizeGroupName(String requestedName) {
        if (requestedName == null) {
            throw new IllegalArgumentException("그룹 이름은 필수입니다.");
        }

        String normalizedName = requestedName.strip();
        if (normalizedName.isEmpty() || normalizedName.length() > MAX_GROUP_NAME_LENGTH) {
            throw new IllegalArgumentException("그룹 이름은 1자 이상 50자 이하여야 합니다.");
        }
        return normalizedName;
    }
}

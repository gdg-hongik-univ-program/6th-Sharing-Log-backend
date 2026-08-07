package gdg.sharinglog.service.group;

import java.time.Instant;
import java.util.Objects;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.GroupInvitation;
import gdg.sharinglog.domain.GroupRole;
import gdg.sharinglog.domain.MemberStatus;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.repository.GroupInvitationRepository;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.service.group.exception.GroupMemberAccessDeniedException;
import gdg.sharinglog.service.group.exception.GroupDeletionConflictException;
import gdg.sharinglog.service.group.exception.GroupNotFoundException;
import gdg.sharinglog.service.group.result.CreatedGroup;
import gdg.sharinglog.service.group.result.UpdatedGroup;
import gdg.sharinglog.service.rotation.occurrence.OccurrenceCommandService;
import gdg.sharinglog.service.rotation.occurrence.OccurrencePlanService;
import gdg.sharinglog.service.user.AuthenticatedUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupService {

    private static final int MAX_GROUP_NAME_LENGTH = 50;

    private final SharingGroupRepository groupRepository;
    private final GroupInvitationRepository groupInvitationRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final OccurrenceCommandService occurrenceCommandService;
    private final OccurrencePlanService occurrencePlanService;

    @Transactional
    public CreatedGroup createGroup(
            String requestedName,
            String requestedAddress,
            String registrationId,
            OAuth2User oAuth2User
    ) {
        String groupName = normalizeGroupName(requestedName);
        User creator = authenticatedUserService.requireUser(registrationId, oAuth2User);

        SharingGroup group = new SharingGroup(groupName, creator);
        group.updateAddress(requestedAddress);
        group = groupRepository.save(group);
        GroupMember ownerMembership = groupMemberRepository.save(GroupMember.owner(group, creator));

        return new CreatedGroup(
                group.getId(),
                group.getPublicId(),
                group.getName(),
                group.getAddress(),
                ownerMembership.getId(),
                ownerMembership.getPublicId(),
                ownerMembership.getRole(),
                group.getCreatedAt()
        );
    }

    @Transactional
    public UpdatedGroup updateGroup(
            String groupPublicId,
            String requestedName,
            String requestedAddress,
            String registrationId,
            OAuth2User oAuth2User
    ) {
        if (requestedName == null && requestedAddress == null) {
            throw new IllegalArgumentException("그룹 이름 또는 주소 중 하나 이상을 입력해 주세요.");
        }

        User requester = authenticatedUserService.requireUserForUpdate(registrationId, oAuth2User);
        SharingGroup group = groupRepository.findByPublicIdForUpdate(groupPublicId)
                .orElseThrow(() -> new GroupNotFoundException(groupPublicId));
        GroupMember membership = groupMemberRepository
                .findByGroup_IdAndUser_IdAndStatus(
                        group.getId(),
                        requester.getId(),
                        MemberStatus.ACTIVE
                )
                .orElseThrow(() -> new GroupMemberAccessDeniedException(
                        "활성 그룹 멤버만 그룹 정보를 수정할 수 있습니다."
                ));
        if (membership.getRole() != GroupRole.OWNER) {
            throw new GroupMemberAccessDeniedException(
                    "그룹 OWNER만 그룹 정보를 수정할 수 있습니다."
            );
        }

        if (requestedName != null) {
            group.updateName(normalizeGroupName(requestedName));
        }
        if (requestedAddress != null) {
            group.updateAddress(requestedAddress);
        }

        return new UpdatedGroup(
                group.getPublicId(),
                group.getName(),
                group.getAddress()
        );
    }

    @Transactional
    public void deleteGroup(
            String groupPublicId,
            String registrationId,
            OAuth2User oAuth2User,
            Instant deletedAt
    ) {
        User requester = authenticatedUserService.requireUserForUpdate(registrationId, oAuth2User);
        SharingGroup group = groupRepository.findByPublicIdForUpdate(groupPublicId)
                .orElseThrow(() -> new GroupNotFoundException(groupPublicId));
        GroupMember membership = groupMemberRepository
                .findByGroup_IdAndUser_IdAndStatus(
                        group.getId(),
                        requester.getId(),
                        MemberStatus.ACTIVE
                )
                .orElseThrow(() -> new GroupMemberAccessDeniedException(
                        "활성 그룹 멤버만 그룹을 삭제할 수 있습니다."
                ));
        if (membership.getRole() != GroupRole.OWNER) {
            throw new GroupMemberAccessDeniedException(
                    "그룹 OWNER만 그룹을 삭제할 수 있습니다."
            );
        }
        if (groupMemberRepository.existsByGroup_IdAndStatusAndIdNot(
                group.getId(),
                MemberStatus.ACTIVE,
                membership.getId()
        )) {
            throw new GroupDeletionConflictException();
        }

        Instant effectiveDeletedAt = Objects.requireNonNull(deletedAt, "그룹 삭제 시각은 필수입니다.");
        occurrenceCommandService.leaveMember(membership.getPublicId(), effectiveDeletedAt);
        occurrencePlanService.cancelGroupFuture(group.getId(), effectiveDeletedAt);
        for (GroupInvitation invitation : groupInvitationRepository.findAllByGroup_Id(group.getId())) {
            if (invitation.getRevokedAt() == null) {
                invitation.revoke(effectiveDeletedAt);
            }
        }
        group.delete(effectiveDeletedAt);
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

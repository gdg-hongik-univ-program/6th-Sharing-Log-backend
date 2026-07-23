package gdg.sharinglog.service.rotation.access;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.GroupRole;
import gdg.sharinglog.domain.MemberStatus;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.service.user.AuthenticatedUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
class RotationActorAccessServiceTest {

    private static final String GROUP_PUBLIC_ID =
            "11111111-1111-4111-8111-111111111111";
    private static final String MEMBERSHIP_PUBLIC_ID =
            "22222222-2222-4222-8222-222222222222";
    private static final String REGISTRATION_ID = "google";
    private static final long GROUP_ID = 11L;
    private static final long USER_ID = 22L;

    @Mock
    SharingGroupRepository groupRepository;

    @Mock
    GroupMemberRepository groupMemberRepository;

    @Mock
    AuthenticatedUserService authenticatedUserService;

    @Mock
    OAuth2User oAuth2User;

    @Mock
    SharingGroup group;

    @Mock
    User user;

    @Mock
    GroupMember membership;

    @Mock
    GroupMember targetMembership;

    @InjectMocks
    RotationActorAccessService accessService;

    @BeforeEach
    void setUpActorEntities() {
        when(group.getId()).thenReturn(GROUP_ID);
        when(group.getPublicId()).thenReturn(GROUP_PUBLIC_ID);
        when(user.getId()).thenReturn(USER_ID);
    }

    @Test
    void resolvesActorFromOAuthUserAndActiveMembership() {
        stubReadActor(GroupRole.MEMBER);

        RotationActor actor = accessService.requireActiveMember(
                GROUP_PUBLIC_ID,
                REGISTRATION_ID,
                oAuth2User
        );

        assertSame(group, actor.group());
        assertSame(membership, actor.membership());
        assertSame(user, actor.user());
        verify(authenticatedUserService).requireUser(REGISTRATION_ID, oAuth2User);
        verify(groupMemberRepository).findByGroup_IdAndUser_IdAndStatus(
                GROUP_ID,
                USER_ID,
                MemberStatus.ACTIVE
        );
    }

    @Test
    void updateAccessLocksGroupByPublicId() {
        stubAuthenticatedUser();
        when(groupRepository.findByPublicIdForUpdate(GROUP_PUBLIC_ID))
                .thenReturn(Optional.of(group));
        stubActiveMembership(GroupRole.MEMBER);

        RotationActor actor = accessService.requireActiveMemberForUpdate(
                GROUP_PUBLIC_ID,
                REGISTRATION_ID,
                oAuth2User
        );

        assertSame(membership, actor.membership());
        verify(groupRepository).findByPublicIdForUpdate(GROUP_PUBLIC_ID);
        verify(groupRepository, never()).findByPublicId(GROUP_PUBLIC_ID);
    }

    @Test
    void leftMemberCannotAct() {
        stubAuthenticatedUser();
        when(groupRepository.findByPublicId(GROUP_PUBLIC_ID)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroup_IdAndUser_IdAndStatus(
                GROUP_ID,
                USER_ID,
                MemberStatus.ACTIVE
        )).thenReturn(Optional.empty());

        assertThrows(
                RotationAccessDeniedException.class,
                () -> accessService.requireActiveMember(
                        GROUP_PUBLIC_ID,
                        REGISTRATION_ID,
                        oAuth2User
                )
        );
    }

    @Test
    void ownerAccessRejectsRegularMember() {
        stubReadActor(GroupRole.MEMBER);

        assertThrows(
                RotationAccessDeniedException.class,
                () -> accessService.requireOwner(
                        GROUP_PUBLIC_ID,
                        REGISTRATION_ID,
                        oAuth2User
                )
        );
    }

    @Test
    void ownerAccessReturnsAuthenticatedOwner() {
        stubReadActor(GroupRole.OWNER);

        RotationActor actor = accessService.requireOwner(
                GROUP_PUBLIC_ID,
                REGISTRATION_ID,
                oAuth2User
        );

        assertSame(membership, actor.membership());
    }

    @Test
    void targetMemberMustBeActiveAndBelongToActorsGroupUuid() {
        RotationActor actor = activeActor(GroupRole.OWNER);
        when(groupMemberRepository.findByPublicIdAndGroup_PublicIdAndStatus(
                MEMBERSHIP_PUBLIC_ID,
                GROUP_PUBLIC_ID,
                MemberStatus.ACTIVE
        )).thenReturn(Optional.of(targetMembership));

        GroupMember result = accessService.requireActiveTargetMember(
                actor,
                MEMBERSHIP_PUBLIC_ID
        );

        assertSame(targetMembership, result);
    }

    @Test
    void targetFromAnotherGroupIsReportedAsNotFound() {
        RotationActor actor = activeActor(GroupRole.OWNER);
        when(groupMemberRepository.findByPublicIdAndGroup_PublicIdAndStatus(
                MEMBERSHIP_PUBLIC_ID,
                GROUP_PUBLIC_ID,
                MemberStatus.ACTIVE
        )).thenReturn(Optional.empty());

        assertThrows(
                RotationMemberNotFoundException.class,
                () -> accessService.requireActiveTargetMember(actor, MEMBERSHIP_PUBLIC_ID)
        );
    }

    @Test
    void targetUpdateUsesScopedPessimisticQuery() {
        RotationActor actor = activeActor(GroupRole.OWNER);
        when(groupMemberRepository.findByPublicIdAndGroupPublicIdAndStatusForUpdate(
                MEMBERSHIP_PUBLIC_ID,
                GROUP_PUBLIC_ID,
                MemberStatus.ACTIVE
        )).thenReturn(Optional.of(targetMembership));

        GroupMember result = accessService.requireActiveTargetMemberForUpdate(
                actor,
                MEMBERSHIP_PUBLIC_ID
        );

        assertSame(targetMembership, result);
        verify(groupMemberRepository)
                .findByPublicIdAndGroupPublicIdAndStatusForUpdate(
                        MEMBERSHIP_PUBLIC_ID,
                        GROUP_PUBLIC_ID,
                        MemberStatus.ACTIVE
                );
    }

    @Test
    void leftActorCannotUseTargetLookup() {
        RotationActor actor = actor(GroupRole.OWNER, false);

        assertThrows(
                RotationAccessDeniedException.class,
                () -> accessService.requireActiveTargetMember(actor, MEMBERSHIP_PUBLIC_ID)
        );
        verify(groupMemberRepository, never())
                .findByPublicIdAndGroup_PublicIdAndStatus(
                        MEMBERSHIP_PUBLIC_ID,
                        GROUP_PUBLIC_ID,
                        MemberStatus.ACTIVE
                );
    }

    private void stubReadActor(GroupRole role) {
        stubAuthenticatedUser();
        when(groupRepository.findByPublicId(GROUP_PUBLIC_ID)).thenReturn(Optional.of(group));
        stubActiveMembership(role);
    }

    private void stubAuthenticatedUser() {
        when(authenticatedUserService.requireUser(REGISTRATION_ID, oAuth2User))
                .thenReturn(user);
    }

    private void stubActiveMembership(GroupRole role) {
        when(groupMemberRepository.findByGroup_IdAndUser_IdAndStatus(
                GROUP_ID,
                USER_ID,
                MemberStatus.ACTIVE
        )).thenReturn(Optional.of(membership));
        when(membership.getRole()).thenReturn(role);
    }

    private RotationActor activeActor(GroupRole role) {
        return actor(role, true);
    }

    private RotationActor actor(GroupRole role, boolean active) {
        when(membership.getRole()).thenReturn(role);
        when(membership.isActive()).thenReturn(active);
        when(membership.getGroup()).thenReturn(group);
        when(membership.getUser()).thenReturn(user);
        return new RotationActor(group, membership, user);
    }
}

package gdg.sharinglog.service;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

import gdg.sharinglog.domain.GroupInvitation;
import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.repository.GroupInvitationRepository;
import gdg.sharinglog.repository.GroupMemberRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationAcceptanceService {

    private static final Pattern INVITATION_CODE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{22}");

    private final GroupInvitationRepository invitationRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final InvitationCodeHasher codeHasher;
    private final AuthenticatedUserService authenticatedUserService;

    public InvitationAcceptanceService(GroupInvitationRepository invitationRepository,
                                       GroupMemberRepository groupMemberRepository,
                                       InvitationCodeHasher codeHasher,
                                       AuthenticatedUserService authenticatedUserService) {
        this.invitationRepository = invitationRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.codeHasher = codeHasher;
        this.authenticatedUserService = authenticatedUserService;
    }

    @Transactional(readOnly = true)
    public InvitationPreview preview(String rawCode, String registrationId, OAuth2User oAuth2User) {
        GroupInvitation invitation = requireUsableInvitation(rawCode, Instant.now());
        User user = authenticatedUserService.requireUser(registrationId, oAuth2User);
        SharingGroup group = invitation.getGroup();
        Optional<GroupMember> membership = groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), user.getId());

        return new InvitationPreview(
                group.getId(),
                group.getName(),
                invitation.getExpiresAt(),
                membership.isPresent(),
                membership.map(GroupMember::getRole).orElse(null)
        );
    }

    @Transactional
    public AcceptedInvitation accept(String rawCode, String registrationId, OAuth2User oAuth2User) {
        String codeHash = requireCodeHash(rawCode);
        User user = authenticatedUserService.requireUserForUpdate(registrationId, oAuth2User);
        GroupInvitation invitation = invitationRepository.findByCodeHash(codeHash)
                .orElseThrow(InvitationNotFoundException::new);
        requireUsable(invitation, Instant.now());

        SharingGroup group = invitation.getGroup();
        Optional<GroupMember> existingMembership = groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), user.getId());
        if (existingMembership.isPresent()) {
            return acceptance(existingMembership.get(), false);
        }

        GroupMember membership = groupMemberRepository.save(GroupMember.member(group, user));
        return acceptance(membership, true);
    }

    private GroupInvitation requireUsableInvitation(String rawCode, Instant now) {
        GroupInvitation invitation = invitationRepository.findByCodeHash(requireCodeHash(rawCode))
                .orElseThrow(InvitationNotFoundException::new);
        requireUsable(invitation, now);
        return invitation;
    }

    private String requireCodeHash(String rawCode) {
        if (rawCode == null || !INVITATION_CODE_PATTERN.matcher(rawCode).matches()) {
            throw new InvitationNotFoundException();
        }
        return codeHasher.hash(rawCode);
    }

    private void requireUsable(GroupInvitation invitation, Instant now) {
        if (!invitation.isUsableAt(now)) {
            throw new InvitationUnavailableException();
        }
    }

    private AcceptedInvitation acceptance(GroupMember membership, boolean joinedNow) {
        return new AcceptedInvitation(
                membership.getGroup().getId(),
                membership.getGroup().getName(),
                membership.getId(),
                membership.getRole(),
                membership.getJoinedAt(),
                joinedNow
        );
    }
}

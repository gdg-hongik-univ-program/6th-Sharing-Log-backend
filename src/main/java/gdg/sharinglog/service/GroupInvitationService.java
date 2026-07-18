package gdg.sharinglog.service;

import java.time.Duration;
import java.time.Instant;

import gdg.sharinglog.domain.GroupInvitation;
import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.GroupRole;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.repository.GroupInvitationRepository;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.SharingGroupRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupInvitationService {

    private static final Duration INVITATION_VALIDITY = Duration.ofHours(24);
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 10;

    private final SharingGroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupInvitationRepository invitationRepository;
    private final InvitationCodeGenerator codeGenerator;
    private final InvitationCodeHasher codeHasher;
    private final AuthenticatedUserService authenticatedUserService;

    public GroupInvitationService(SharingGroupRepository groupRepository,
                                  GroupMemberRepository groupMemberRepository,
                                  GroupInvitationRepository invitationRepository,
                                  InvitationCodeGenerator codeGenerator,
                                  InvitationCodeHasher codeHasher,
                                  AuthenticatedUserService authenticatedUserService) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.invitationRepository = invitationRepository;
        this.codeGenerator = codeGenerator;
        this.codeHasher = codeHasher;
        this.authenticatedUserService = authenticatedUserService;
    }

    @Transactional
    public IssuedInvitation issue(Long groupId, String registrationId, OAuth2User oAuth2User) {
        User requester = authenticatedUserService.requireUser(registrationId, oAuth2User);
        SharingGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
        GroupMember membership = groupMemberRepository
                .findByGroup_IdAndUser_Id(groupId, requester.getId())
                .orElseThrow(InvitationPermissionDeniedException::new);

        if (membership.getRole() != GroupRole.OWNER) {
            throw new InvitationPermissionDeniedException();
        }

        Instant createdAt = Instant.now();
        String code = nextUniqueCode();
        GroupInvitation invitation = invitationRepository.save(new GroupInvitation(
                group,
                requester,
                codeHasher.hash(code),
                createdAt,
                createdAt.plus(INVITATION_VALIDITY)
        ));

        return new IssuedInvitation(
                invitation.getId(),
                group.getId(),
                code,
                invitation.getCreatedAt(),
                invitation.getExpiresAt()
        );
    }

    private String nextUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = codeGenerator.generate();
            if (!invitationRepository.existsByCodeHash(codeHasher.hash(code))) {
                return code;
            }
        }
        throw new IllegalStateException("고유한 초대 코드를 생성하지 못했습니다.");
    }
}

package gdg.sharinglog.service.rotation.api.substitute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.OAuthProvider;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.domain.rotation.AssignmentEndReason;
import gdg.sharinglog.domain.rotation.AssignmentTrigger;
import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreEligibilityMode;
import gdg.sharinglog.domain.rotation.SubstituteRecipientStatus;
import gdg.sharinglog.domain.rotation.SubstituteRequestStatus;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.repository.UserRepository;
import gdg.sharinglog.repository.rotation.ChoreAssignmentAttemptRepository;
import gdg.sharinglog.repository.rotation.ChoreRepository;
import gdg.sharinglog.repository.rotation.RotationDecisionLogRepository;
import gdg.sharinglog.service.rotation.assignment.DirectAssignmentService;
import gdg.sharinglog.service.rotation.occurrence.OccurrenceGenerationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SubstituteRequestApplicationServiceTest {

    private static final Instant REFERENCE = Instant.parse("2026-07-23T03:00:00Z");

    @Autowired
    SubstituteRequestApplicationService service;

    @Autowired
    OccurrenceGenerationService generationService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SharingGroupRepository groupRepository;

    @Autowired
    GroupMemberRepository memberRepository;

    @Autowired
    ChoreRepository choreRepository;

    @Autowired
    ChoreAssignmentAttemptRepository assignmentRepository;

    @Autowired
    RotationDecisionLogRepository decisionLogRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void createdRequestAppearsInRequesterOutbox() {
        List<GroupMember> members = members();
        SharingGroup group = members.getFirst().getGroup();
        Chore chore = choreRepository.save(Chore.daily(
                group,
                members.getFirst(),
                "공용 청소",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalTime.of(21, 0),
                REFERENCE.minusSeconds(60)
        ));
        var occurrence = generationService.ensureCurrentOccurrence(chore.getId(), REFERENCE);
        entityManager.flush();
        entityManager.refresh(occurrence);
        GroupMember requester = occurrence.currentAssignee().orElseThrow();

        var created = service.create(
                group.getPublicId(),
                occurrence.getPublicId(),
                "google",
                principal(requester),
                occurrence.getVersion(),
                "외부 일정이 있어요.",
                REFERENCE.plusSeconds(60)
        );

        var outbox = service.findAll(
                group.getPublicId(),
                "google",
                principal(requester),
                SubstituteRequestBox.OUTBOX,
                null
        );

        assertEquals(SubstituteRequestBox.OUTBOX, outbox.box());
        assertEquals(1, outbox.totalCount());
        assertEquals(created.requestId(), outbox.items().getFirst().requestId());
    }

    @Test
    void exhaustedRequestCanBeRetriedAndFirstAcceptanceTransfersAssignment() {
        List<GroupMember> members = members();
        SharingGroup group = members.getFirst().getGroup();
        Chore chore = choreRepository.save(Chore.daily(
                group,
                members.getFirst(),
                "공용 청소",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalTime.of(21, 0),
                REFERENCE.minusSeconds(60)
        ));
        var occurrence = generationService.ensureCurrentOccurrence(chore.getId(), REFERENCE);
        entityManager.flush();
        entityManager.refresh(occurrence);
        GroupMember requester = occurrence.currentAssignee().orElseThrow();
        List<GroupMember> recipients = members.stream()
                .filter(member -> !member.getId().equals(requester.getId()))
                .toList();

        var created = service.create(
                group.getPublicId(),
                occurrence.getPublicId(),
                "google",
                principal(requester),
                occurrence.getVersion(),
                "외부 일정이 있어요.",
                REFERENCE.plusSeconds(60)
        );

        assertEquals(SubstituteRequestStatus.PENDING, created.status());
        assertEquals(requester.getPublicId(),
                created.occurrence().currentAssignee().membershipId());
        assertEquals(2, created.recipients().size());

        var rejected = service.decline(
                group.getPublicId(),
                created.requestId(),
                "google",
                principal(recipients.getFirst()),
                created.version(),
                REFERENCE.plusSeconds(120)
        );

        assertEquals(SubstituteRequestStatus.PENDING, rejected.status());
        assertEquals(requester.getPublicId(),
                rejected.occurrence().currentAssignee().membershipId());
        assertTrue(rejected.recipients().stream().anyMatch(recipient ->
                recipient.member().membershipId().equals(recipients.getFirst().getPublicId())
                        && recipient.status() == SubstituteRecipientStatus.DECLINED
        ));

        var exhausted = service.decline(
                group.getPublicId(),
                created.requestId(),
                "google",
                principal(recipients.getLast()),
                rejected.version(),
                REFERENCE.plusSeconds(180)
        );

        assertEquals(SubstituteRequestStatus.EXHAUSTED, exhausted.status());
        assertEquals(requester.getPublicId(),
                exhausted.occurrence().currentAssignee().membershipId());

        var retried = service.create(
                group.getPublicId(),
                occurrence.getPublicId(),
                "google",
                principal(requester),
                occurrence.getVersion(),
                "아직 대타가 필요해요.",
                REFERENCE.plusSeconds(240)
        );
        var accepted = service.accept(
                group.getPublicId(),
                retried.requestId(),
                "google",
                principal(recipients.getLast()),
                retried.version(),
                REFERENCE.plusSeconds(300)
        );

        var attempts = assignmentRepository
                .findAllByOccurrence_IdOrderBySequenceNumber(occurrence.getId());
        assertEquals(SubstituteRequestStatus.ACCEPTED, accepted.status());
        assertEquals(recipients.getLast().getPublicId(),
                accepted.occurrence().currentAssignee().membershipId());
        assertEquals(AssignmentEndReason.SUBSTITUTE_ACCEPTED,
                attempts.getFirst().getEndReason());
        assertEquals(AssignmentTrigger.SUBSTITUTE_ACCEPTANCE,
                attempts.getLast().getTrigger());
        assertEquals(DirectAssignmentService.ALGORITHM_VERSION,
                attempts.getLast().getAlgorithmVersion());
        assertEquals(0L, attempts.getLast().getDecisionSeed());
        assertEquals(2, decisionLogRepository.countByOccurrence_Id(occurrence.getId()));
    }

    private List<GroupMember> members() {
        User owner = userRepository.save(user("substitute-owner"));
        User first = userRepository.save(user("substitute-first"));
        User second = userRepository.save(user("substitute-second"));
        SharingGroup group = groupRepository.save(new SharingGroup("우리 집", owner));
        return List.of(
                memberRepository.save(GroupMember.owner(group, owner)),
                memberRepository.save(GroupMember.member(group, first)),
                memberRepository.save(GroupMember.member(group, second))
        );
    }

    private OAuth2User principal(GroupMember member) {
        Map<String, Object> attributes =
                Map.of("sub", member.getUser().getProviderUserId());
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "sub"
        );
    }

    private User user(String providerUserId) {
        return User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(providerUserId + "-" + System.nanoTime())
                .build();
    }
}

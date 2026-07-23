package gdg.sharinglog.service.rotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalTime;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.OAuthProvider;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreEligibleMember;
import gdg.sharinglog.domain.rotation.ChoreEligibilityMode;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.repository.UserRepository;
import gdg.sharinglog.repository.rotation.ChoreAssignmentAttemptRepository;
import gdg.sharinglog.repository.rotation.ChoreEligibleMemberRepository;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import gdg.sharinglog.repository.rotation.ChoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OccurrenceGenerationServiceTest {

    @Autowired
    OccurrenceGenerationService generationService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SharingGroupRepository groupRepository;

    @Autowired
    GroupMemberRepository groupMemberRepository;

    @Autowired
    ChoreRepository choreRepository;

    @Autowired
    ChoreEligibleMemberRepository choreEligibleMemberRepository;

    @Autowired
    ChoreOccurrenceRepository occurrenceRepository;

    @Autowired
    ChoreAssignmentAttemptRepository assignmentRepository;

    @Test
    void createsAndAssignsCurrentOccurrenceIdempotently() {
        Context context = context("all-active");
        Chore chore = choreRepository.save(Chore.daily(
                context.group(),
                context.ownerMembership(),
                "설거지",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalTime.of(22, 0),
                Instant.parse("2026-07-23T00:00:00Z")
        ));
        Instant reference = Instant.parse("2026-07-23T03:00:00Z");

        ChoreOccurrence first = generationService.ensureCurrentOccurrence(chore.getId(), reference);
        ChoreOccurrence second = generationService.ensureCurrentOccurrence(chore.getId(), reference);

        assertSame(first, second);
        assertEquals(1, occurrenceRepository.count());
        assertEquals(1, assignmentRepository.count());
        assertEquals(OccurrenceStatus.ASSIGNED, first.getStatus());
        assertEquals(
                context.ownerMembership().getId(),
                first.currentAssignee().orElseThrow().getId()
        );
    }

    @Test
    void selectedMemberRestrictionExcludesOtherActiveMembers() {
        Context context = context("selected");
        User selectedUser = userRepository.save(user("selected-member"));
        GroupMember selected = groupMemberRepository.save(
                GroupMember.member(context.group(), selectedUser)
        );
        Chore chore = choreRepository.save(Chore.daily(
                context.group(),
                context.ownerMembership(),
                "가스 점검",
                ChoreEligibilityMode.SELECTED_MEMBERS,
                LocalTime.of(10, 0),
                Instant.parse("2026-07-23T00:00:00Z")
        ));
        choreEligibleMemberRepository.save(new ChoreEligibleMember(chore, selected));

        ChoreOccurrence occurrence = generationService.ensureCurrentOccurrence(
                chore.getId(),
                Instant.parse("2026-07-23T03:00:00Z")
        );

        assertEquals(OccurrenceStatus.ASSIGNED, occurrence.getStatus());
        assertEquals(selected.getId(), occurrence.currentAssignee().orElseThrow().getId());
        assertTrue(
                assignmentRepository
                        .findFirstByOccurrence_IdAndEndedAtIsNull(occurrence.getId())
                        .orElseThrow()
                        .getCandidateSnapshot()
                        .contains("decision=NOT_ELIGIBLE")
        );
    }

    @Test
    void missingSelectedCandidatesCreatesNeedsAttentionOccurrence() {
        Context context = context("no-selected");
        Chore chore = choreRepository.save(Chore.daily(
                context.group(),
                context.ownerMembership(),
                "보일러 점검",
                ChoreEligibilityMode.SELECTED_MEMBERS,
                LocalTime.of(10, 0),
                Instant.parse("2026-07-23T00:00:00Z")
        ));

        ChoreOccurrence occurrence = generationService.ensureCurrentOccurrence(
                chore.getId(),
                Instant.parse("2026-07-23T03:00:00Z")
        );

        assertEquals(OccurrenceStatus.NEEDS_ATTENTION, occurrence.getStatus());
        assertTrue(occurrence.currentAssignee().isEmpty());
        assertEquals(0, assignmentRepository.count());
    }

    private Context context(String suffix) {
        User owner = userRepository.save(user("owner-" + suffix));
        SharingGroup group = groupRepository.save(new SharingGroup("우리 집", owner));
        GroupMember membership = groupMemberRepository.save(GroupMember.owner(group, owner));
        return new Context(group, membership);
    }

    private User user(String providerUserId) {
        return User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(providerUserId + "-" + System.nanoTime())
                .build();
    }

    private record Context(SharingGroup group, GroupMember ownerMembership) {
    }
}

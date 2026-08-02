package gdg.sharinglog.service.rotation.occurrence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.OAuthProvider;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreEligibleMember;
import gdg.sharinglog.domain.rotation.ChoreEligibilityMode;
import gdg.sharinglog.domain.rotation.ChoreFrequency;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.NoCandidateReason;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.repository.UserRepository;
import gdg.sharinglog.repository.rotation.ChoreAssignmentAttemptRepository;
import gdg.sharinglog.repository.rotation.ChoreEligibleMemberRepository;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import gdg.sharinglog.repository.rotation.ChoreRepository;
import gdg.sharinglog.repository.rotation.RotationDecisionLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

    @Autowired
    RotationDecisionLogRepository decisionLogRepository;

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
        assertEquals(1, decisionLogRepository.count());
        assertEquals(OccurrenceStatus.ASSIGNED, first.getStatus());
        assertEquals(
                context.ownerMembership().getId(),
                first.currentAssignee().orElseThrow().getId()
        );
    }

    @ParameterizedTest
    @EnumSource(ChoreFrequency.class)
    void createsOccurrenceWhenDatabaseRoundsHighPrecisionCreationTime(
            ChoreFrequency frequency
    ) {
        Context context = context("high-precision-" + frequency);
        Instant reference = Instant.parse("2026-07-23T03:00:00.123456789Z");
        Chore chore = switch (frequency) {
            case DAILY -> Chore.daily(
                    context.group(),
                    context.ownerMembership(),
                    "일간 업무",
                    ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                    LocalTime.of(20, 0),
                    reference
            );
            case WEEKLY -> Chore.weekly(
                    context.group(),
                    context.ownerMembership(),
                    "주간 업무",
                    ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                    DayOfWeek.SUNDAY,
                    LocalTime.of(20, 0),
                    reference
            );
            case BIWEEKLY -> Chore.biweekly(
                    context.group(),
                    context.ownerMembership(),
                    "격주 업무",
                    ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                    LocalDate.of(2026, 7, 20),
                    LocalTime.of(20, 0),
                    reference
            );
        };
        choreRepository.save(chore);

        ChoreOccurrence occurrence =
                generationService.ensureCurrentOccurrence(chore.getId(), reference);

        assertEquals(OccurrenceStatus.ASSIGNED, occurrence.getStatus());
        assertEquals(1, assignmentRepository.countByOccurrence_Id(occurrence.getId()));
        assertEquals(1, decisionLogRepository.countByOccurrence_Id(occurrence.getId()));
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

        Instant reference = Instant.parse("2026-07-23T03:00:00Z");
        ChoreOccurrence occurrence =
                generationService.ensureCurrentOccurrence(chore.getId(), reference);

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

        Instant reference = Instant.parse("2026-07-23T03:00:00Z");
        ChoreOccurrence occurrence =
                generationService.ensureCurrentOccurrence(chore.getId(), reference);

        assertEquals(OccurrenceStatus.NEEDS_ATTENTION, occurrence.getStatus());
        assertTrue(occurrence.currentAssignee().isEmpty());
        assertEquals(0, assignmentRepository.count());
        assertEquals(1, decisionLogRepository.count());
        assertEquals(
                "NO_CANDIDATE",
                decisionLogRepository.findAll().getFirst().getOutcome().name()
        );
        assertEquals(
                NoCandidateReason.NO_ACTIVE_ELIGIBLE_NON_DECLINED_CANDIDATE,
                occurrence.getAttentionReason()
        );
        assertEquals(reference, occurrence.getAttentionSince());
        assertEquals(reference, occurrence.getLastDecisionAt());
    }

    @Test
    void scheduleChangeMovesActiveOccurrenceAndKeepsAssignmentAndStatus() {
        Context context = context("schedule-change");
        Chore chore = choreRepository.save(Chore.daily(
                context.group(),
                context.ownerMembership(),
                "공용 청소",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalTime.of(20, 0),
                Instant.parse("2026-07-23T00:00:00Z")
        ));
        ChoreOccurrence daily = generationService.ensureCurrentOccurrence(
                chore.getId(),
                Instant.parse("2026-07-23T03:00:00Z")
        );
        Long assigneeId = daily.currentAssignee().orElseThrow().getId();

        chore.reschedule(
                ChoreFrequency.WEEKLY,
                LocalTime.of(19, 0),
                DayOfWeek.SUNDAY,
                null
        );
        choreRepository.saveAndFlush(chore);

        ChoreOccurrence rescheduled = generationService.rescheduleActiveOccurrence(
                chore,
                Instant.parse("2026-07-23T04:00:00Z")
        ).orElseThrow();
        ChoreOccurrence ensured = generationService.ensureCurrentOccurrence(
                chore.getId(),
                Instant.parse("2026-07-23T04:00:00Z")
        );

        assertSame(daily, rescheduled);
        assertSame(daily, ensured);
        assertEquals(1, occurrenceRepository.count());
        assertEquals(1L, chore.getScheduleRevision());
        assertEquals(1L, daily.getScheduleRevisionSnapshot());
        assertEquals(ChoreFrequency.WEEKLY, daily.getFrequencySnapshot());
        assertEquals(LocalDate.of(2026, 7, 20), daily.getPeriodStart());
        assertEquals(LocalDate.of(2026, 7, 27), daily.getPeriodEndExclusive());
        assertEquals(Instant.parse("2026-07-26T10:00:00Z"), daily.getDueAt());
        assertEquals(OccurrenceStatus.ASSIGNED, daily.getStatus());
        assertEquals(assigneeId, daily.currentAssignee().orElseThrow().getId());
    }

    @Test
    void completedOccurrenceKeepsItsSnapshotAndNextGenerationUsesNewRevision() {
        Context context = context("completed-schedule-change");
        Chore chore = choreRepository.save(Chore.daily(
                context.group(),
                context.ownerMembership(),
                "공용 청소",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalTime.of(20, 0),
                Instant.parse("2026-07-23T00:00:00Z")
        ));
        ChoreOccurrence completed = generationService.ensureCurrentOccurrence(
                chore.getId(),
                Instant.parse("2026-07-23T03:00:00Z")
        );
        completed.complete(Instant.parse("2026-07-23T04:00:00Z"));
        occurrenceRepository.saveAndFlush(completed);

        chore.reschedule(ChoreFrequency.DAILY, LocalTime.of(19, 0), null, null);
        choreRepository.saveAndFlush(chore);

        assertTrue(generationService.rescheduleActiveOccurrence(
                chore,
                Instant.parse("2026-07-23T05:00:00Z")
        ).isEmpty());
        assertEquals(1, occurrenceRepository.count());
        assertEquals(0L, completed.getScheduleRevisionSnapshot());
        assertEquals(Instant.parse("2026-07-23T11:00:00Z"), completed.getDueAt());

        ChoreOccurrence overlapping = generationService.ensureCurrentOccurrence(
                chore.getId(),
                Instant.parse("2026-07-23T05:00:00Z")
        );

        assertSame(completed, overlapping);
        assertEquals(1, occurrenceRepository.count());

        ChoreOccurrence next = generationService.ensureCurrentOccurrence(
                chore.getId(),
                Instant.parse("2026-07-24T05:00:00Z")
        );

        assertEquals(2, occurrenceRepository.count());
        assertEquals(LocalDate.of(2026, 7, 24), next.getPeriodStart());
        assertEquals(1L, next.getScheduleRevisionSnapshot());
        assertEquals(Instant.parse("2026-07-24T10:00:00Z"), next.getDueAt());
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

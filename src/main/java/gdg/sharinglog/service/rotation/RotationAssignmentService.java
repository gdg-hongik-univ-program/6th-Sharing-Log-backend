package gdg.sharinglog.service.rotation;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.rotation.AssignmentEndReason;
import gdg.sharinglog.domain.rotation.AssignmentTrigger;
import gdg.sharinglog.domain.rotation.ChoreAssignmentAttempt;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.rotation.ChoreAssignmentAttemptRepository;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import gdg.sharinglog.repository.rotation.OccurrenceEligibleMemberRepository;
import gdg.sharinglog.rotation.engine.RotationAssignmentEngine;
import gdg.sharinglog.rotation.engine.RotationAssignmentResult;
import gdg.sharinglog.rotation.engine.RotationCandidate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RotationAssignmentService {

    public static final String ALGORITHM_VERSION = "fair-random-v1";

    private final GroupMemberRepository groupMemberRepository;
    private final ChoreOccurrenceRepository occurrenceRepository;
    private final ChoreAssignmentAttemptRepository assignmentRepository;
    private final OccurrenceEligibleMemberRepository eligibilityRepository;
    private final DecisionSeedGenerator seedGenerator;

    public RotationAssignmentService(
            GroupMemberRepository groupMemberRepository,
            ChoreOccurrenceRepository occurrenceRepository,
            ChoreAssignmentAttemptRepository assignmentRepository,
            OccurrenceEligibleMemberRepository eligibilityRepository,
            DecisionSeedGenerator seedGenerator
    ) {
        this.groupMemberRepository = groupMemberRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.assignmentRepository = assignmentRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.seedGenerator = seedGenerator;
    }

    @Transactional
    public RotationAssignmentResult assign(
            ChoreOccurrence occurrence,
            AssignmentTrigger trigger,
            java.time.Instant assignedAt
    ) {
        Objects.requireNonNull(occurrence, "회차는 필수입니다.");
        Objects.requireNonNull(trigger, "배정 계기는 필수입니다.");
        Objects.requireNonNull(assignedAt, "배정 시각은 필수입니다.");
        if (occurrence.getId() == null) {
            throw new IllegalArgumentException("저장된 회차만 배정할 수 있습니다.");
        }
        if (occurrence.getStatus() != OccurrenceStatus.NEEDS_ATTENTION) {
            throw new IllegalStateException("관리 필요 상태의 회차만 새 담당자를 배정할 수 있습니다.");
        }

        List<GroupMember> groupMembers = groupMemberRepository.findAllByGroup_Id(
                occurrence.getChore().getGroup().getId()
        );
        Set<Long> eligibleMembershipIds = currentEligibleMembershipIds(occurrence);
        Optional<Long> previousAssigneeId = previousAssigneeId(occurrence);
        Map<Long, GroupMember> membersById = new HashMap<>();

        List<RotationCandidate> candidates = groupMembers.stream()
                .peek(member -> membersById.put(member.getId(), member))
                .map(member -> candidate(
                        occurrence,
                        member,
                        eligibleMembershipIds.contains(member.getId()),
                        previousAssigneeId.filter(member.getId()::equals).isPresent()
                ))
                .toList();

        long decisionSeed = seedGenerator.nextSeed();
        RotationAssignmentResult result =
                new RotationAssignmentEngine(new Random(decisionSeed)).assign(candidates);

        if (result instanceof RotationAssignmentResult.Assigned assigned) {
            GroupMember selectedMember = Objects.requireNonNull(
                    membersById.get(assigned.selectedMembershipId()),
                    "선택된 멤버가 그룹 후보 목록에 없습니다."
            );
            int sequenceNumber = Math.toIntExact(
                    assignmentRepository.countByOccurrence_Id(occurrence.getId()) + 1
            );
            ChoreAssignmentAttempt attempt = ChoreAssignmentAttempt.assigned(
                    occurrence,
                    selectedMember,
                    sequenceNumber,
                    trigger,
                    assignedAt,
                    ALGORITHM_VERSION,
                    decisionSeed,
                    CandidateAuditFormatter.snapshot(result.candidateSnapshot()),
                    CandidateAuditFormatter.summary(result.selectionReasons())
            );
            assignmentRepository.save(attempt);
            occurrence.assign(attempt);
            occurrenceRepository.save(occurrence);
        }

        return result;
    }

    private Set<Long> currentEligibleMembershipIds(ChoreOccurrence occurrence) {
        return eligibilityRepository
                .findAllByOccurrence_IdAndSnapshotVersionOrderById(
                        occurrence.getId(),
                        occurrence.getEligibilitySnapshotVersion()
                )
                .stream()
                .map(snapshot -> snapshot.getMember().getId())
                .collect(HashSet::new, HashSet::add, HashSet::addAll);
    }

    private Optional<Long> previousAssigneeId(ChoreOccurrence occurrence) {
        LocalDate periodStart = occurrence.getPeriodStart();
        return occurrenceRepository
                .findFirstByChore_IdAndPeriodStartBeforeOrderByPeriodStartDesc(
                        occurrence.getChore().getId(),
                        periodStart
                )
                .flatMap(previous -> assignmentRepository
                        .findFirstByOccurrence_IdOrderBySequenceNumberDesc(previous.getId()))
                .map(attempt -> attempt.getAssignee().getId());
    }

    private RotationCandidate candidate(
            ChoreOccurrence occurrence,
            GroupMember member,
            boolean eligible,
            boolean previousAssignee
    ) {
        long completedSameChoreCount = assignmentRepository.countCompletedForChoreAndMember(
                occurrence.getChore().getId(),
                member.getId()
        );
        long activePeriodLoad = assignmentRepository.countActiveOrCompletedPeriodLoad(
                occurrence.getChore().getGroup().getId(),
                occurrence.getFrequencySnapshot(),
                occurrence.getPeriodStart(),
                member.getId()
        );
        boolean declined = assignmentRepository
                .existsByOccurrence_IdAndAssignee_IdAndEndReason(
                        occurrence.getId(),
                        member.getId(),
                        AssignmentEndReason.DECLINED_BY_ASSIGNEE
                );

        return new RotationCandidate(
                member.getId(),
                member.isActive(),
                eligible,
                declined,
                Math.toIntExact(completedSameChoreCount),
                Math.toIntExact(activePeriodLoad),
                previousAssignee
        );
    }
}

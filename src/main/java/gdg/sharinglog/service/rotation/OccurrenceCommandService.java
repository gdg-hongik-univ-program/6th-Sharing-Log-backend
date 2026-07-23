package gdg.sharinglog.service.rotation;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.rotation.AssignmentEndReason;
import gdg.sharinglog.domain.rotation.AssignmentTrigger;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.rotation.ChoreAssignmentAttemptRepository;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import gdg.sharinglog.repository.rotation.OccurrenceEligibleMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OccurrenceCommandService {

    private final GroupMemberRepository groupMemberRepository;
    private final ChoreOccurrenceRepository occurrenceRepository;
    private final ChoreAssignmentAttemptRepository assignmentRepository;
    private final OccurrenceEligibleMemberRepository eligibilityRepository;
    private final RotationAssignmentService assignmentService;

    public OccurrenceCommandService(
            GroupMemberRepository groupMemberRepository,
            ChoreOccurrenceRepository occurrenceRepository,
            ChoreAssignmentAttemptRepository assignmentRepository,
            OccurrenceEligibleMemberRepository eligibilityRepository,
            RotationAssignmentService assignmentService
    ) {
        this.groupMemberRepository = groupMemberRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.assignmentRepository = assignmentRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.assignmentService = assignmentService;
    }

    @Transactional
    public ChoreOccurrence complete(
            String occurrencePublicId,
            String actorMemberPublicId,
            Instant completedAt
    ) {
        ChoreOccurrence occurrence = lockedOccurrence(occurrencePublicId);
        GroupMember actor = requireActorOfOccurrence(actorMemberPublicId, occurrence);
        if (occurrence.getStatus() == OccurrenceStatus.COMPLETED) {
            return occurrence;
        }
        requireCurrentAssignee(occurrence, actor);
        occurrence.complete(Objects.requireNonNull(completedAt, "완료 시각은 필수입니다."));
        return occurrenceRepository.save(occurrence);
    }

    @Transactional
    public ChoreOccurrence skipAlreadyDone(
            String occurrencePublicId,
            String actorMemberPublicId,
            Instant skippedAt
    ) {
        ChoreOccurrence occurrence = lockedOccurrence(occurrencePublicId);
        GroupMember actor = requireActorOfOccurrence(actorMemberPublicId, occurrence);
        if (occurrence.getStatus() == OccurrenceStatus.SKIPPED) {
            return occurrence;
        }
        requireCurrentAssignee(occurrence, actor);
        occurrence.skipAlreadyDone(Objects.requireNonNull(skippedAt, "생략 시각은 필수입니다."));
        return occurrenceRepository.save(occurrence);
    }

    @Transactional
    public ChoreOccurrence declineCurrentOccurrence(
            String occurrencePublicId,
            String actorMemberPublicId,
            Instant declinedAt
    ) {
        ChoreOccurrence occurrence = lockedOccurrence(occurrencePublicId);
        GroupMember actor = requireActorOfOccurrence(actorMemberPublicId, occurrence);
        if (assignmentRepository.existsByOccurrence_IdAndAssignee_IdAndEndReason(
                occurrence.getId(),
                actor.getId(),
                AssignmentEndReason.DECLINED_BY_ASSIGNEE
        )) {
            return occurrence;
        }

        requireCurrentAssignee(occurrence, actor);
        Instant effectiveDeclinedAt = Objects.requireNonNull(declinedAt, "수행 불가 시각은 필수입니다.");
        occurrence.releaseForReassignment(
                AssignmentEndReason.DECLINED_BY_ASSIGNEE,
                effectiveDeclinedAt
        );
        occurrenceRepository.saveAndFlush(occurrence);
        assignmentService.assign(
                occurrence,
                AssignmentTrigger.DECLINE_REASSIGNMENT,
                effectiveDeclinedAt
        );
        return occurrence;
    }

    @Transactional
    public List<ChoreOccurrence> leaveMember(
            String memberPublicId,
            Instant leftAt
    ) {
        GroupMember member = groupMemberRepository.findByPublicIdForUpdate(memberPublicId)
                .orElseThrow(() -> new MemberNotFoundException(memberPublicId));
        if (!member.isActive()) {
            return List.of();
        }

        List<ChoreOccurrence> affected = occurrenceRepository
                .findAllByCurrentAssignment_Assignee_IdAndStatus(
                        member.getId(),
                        OccurrenceStatus.ASSIGNED
                )
                .stream()
                .sorted(Comparator
                        .comparingLong(this::eligibleCandidateCount)
                        .thenComparing(ChoreOccurrence::getId))
                .toList();

        Instant effectiveLeftAt = Objects.requireNonNull(leftAt, "탈퇴 시각은 필수입니다.");
        member.leave(effectiveLeftAt);
        groupMemberRepository.saveAndFlush(member);

        for (ChoreOccurrence occurrence : affected) {
            occurrence.releaseForReassignment(
                    AssignmentEndReason.ASSIGNEE_LEFT_GROUP,
                    effectiveLeftAt
            );
            occurrenceRepository.saveAndFlush(occurrence);
            assignmentService.assign(
                    occurrence,
                    AssignmentTrigger.MEMBER_LEFT_REASSIGNMENT,
                    effectiveLeftAt
            );
        }
        return affected;
    }

    private ChoreOccurrence lockedOccurrence(String occurrencePublicId) {
        return occurrenceRepository
                .findByPublicIdForUpdate(
                        Objects.requireNonNull(occurrencePublicId, "회차 공개 ID는 필수입니다.")
                )
                .orElseThrow(() -> new OccurrenceNotFoundException(occurrencePublicId));
    }

    private GroupMember requireActorOfOccurrence(
            String actorMemberPublicId,
            ChoreOccurrence occurrence
    ) {
        GroupMember actor = groupMemberRepository
                .findByPublicIdForUpdate(
                        Objects.requireNonNull(actorMemberPublicId, "멤버 공개 ID는 필수입니다.")
                )
                .orElseThrow(() -> new MemberNotFoundException(actorMemberPublicId));
        if (actor.getGroup().getId() == null
                || !actor.getGroup().getId().equals(occurrence.getChore().getGroup().getId())) {
            throw new OccurrenceCommandConflictException("회차와 같은 그룹의 멤버만 처리할 수 있습니다.");
        }
        return actor;
    }

    private void requireCurrentAssignee(ChoreOccurrence occurrence, GroupMember actor) {
        if (occurrence.getStatus() != OccurrenceStatus.ASSIGNED) {
            throw new OccurrenceCommandConflictException(
                    "현재 배정 상태의 회차만 처리할 수 있습니다."
            );
        }
        GroupMember assignee = occurrence.currentAssignee()
                .orElseThrow(() -> new IllegalStateException("배정 상태에 현재 담당자가 없습니다."));
        if (!assignee.getId().equals(actor.getId())) {
            throw new OccurrenceCommandConflictException("현재 담당자만 이 작업을 처리할 수 있습니다.");
        }
    }

    private long eligibleCandidateCount(ChoreOccurrence occurrence) {
        return eligibilityRepository.countByOccurrence_IdAndSnapshotVersion(
                occurrence.getId(),
                occurrence.getEligibilitySnapshotVersion()
        );
    }
}

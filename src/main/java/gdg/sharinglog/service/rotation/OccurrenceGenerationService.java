package gdg.sharinglog.service.rotation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.rotation.AssignmentTrigger;
import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.OccurrenceEligibleMember;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import gdg.sharinglog.repository.rotation.ChoreRepository;
import gdg.sharinglog.repository.rotation.OccurrenceEligibleMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OccurrenceGenerationService {

    private final SharingGroupRepository sharingGroupRepository;
    private final ChoreRepository choreRepository;
    private final ChoreOccurrenceRepository occurrenceRepository;
    private final OccurrenceEligibleMemberRepository occurrenceEligibleMemberRepository;
    private final ChoreOccurrenceScheduleResolver scheduleResolver;
    private final ChoreEnrollmentService enrollmentService;
    private final RotationAssignmentService assignmentService;

    @Transactional
    public ChoreOccurrence ensureCurrentOccurrence(Long choreId, Instant referenceInstant) {
        Objects.requireNonNull(choreId, "업무 ID는 필수입니다.");
        Objects.requireNonNull(referenceInstant, "기준 시각은 필수입니다.");
        Long groupId = choreRepository.findGroupIdById(choreId)
                .orElseThrow(() -> new ChoreNotFoundException(choreId));
        sharingGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new IllegalStateException("업무의 그룹을 찾을 수 없습니다."));
        Chore chore = choreRepository.findByIdForUpdate(choreId)
                .orElseThrow(() -> new ChoreNotFoundException(choreId));
        if (!chore.isActive()) {
            throw new IllegalStateException("비활성 업무의 새 회차는 생성할 수 없습니다.");
        }

        OccurrenceSchedule schedule = scheduleResolver.resolve(chore, referenceInstant);
        var exactOccurrence = occurrenceRepository
                .findByChore_IdAndPeriodStart(chore.getId(), schedule.periodStart());
        if (exactOccurrence.isPresent()) {
            return exactOccurrence.get();
        }

        var latestOccurrence = occurrenceRepository
                .findFirstByChore_IdOrderByPeriodEndExclusiveDescIdDesc(chore.getId());
        if (latestOccurrence.isPresent()
                && schedule.periodStart().isBefore(
                        latestOccurrence.get().getPeriodEndExclusive()
                )) {
            return latestOccurrence.get();
        }
        return createAndAssign(chore, schedule, referenceInstant);
    }

    private ChoreOccurrence createAndAssign(
            Chore chore,
            OccurrenceSchedule schedule,
            Instant createdAt
    ) {
        ChoreOccurrence occurrence = occurrenceRepository.saveAndFlush(
                ChoreOccurrence.create(
                        chore,
                        schedule.periodStart(),
                        schedule.periodEndExclusive(),
                        schedule.dueAt(),
                        createdAt
                )
        );

        enrollmentService.synchronizeAutomaticEnrollments(chore, createdAt);
        List<GroupMember> eligibleMembers = enrollmentService.findActiveMembers(chore);
        List<OccurrenceEligibleMember> snapshots = eligibleMembers.stream()
                .map(member -> new OccurrenceEligibleMember(
                        occurrence,
                        occurrence.getEligibilitySnapshotVersion(),
                        member,
                        createdAt
                ))
                .toList();
        occurrenceEligibleMemberRepository.saveAllAndFlush(snapshots);
        assignmentService.assign(occurrence.getId(), AssignmentTrigger.INITIAL, createdAt);
        return occurrence;
    }

}

package gdg.sharinglog.service.rotation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.MemberStatus;
import gdg.sharinglog.domain.rotation.AssignmentTrigger;
import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreEligibilityMode;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.OccurrenceEligibleMember;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.rotation.ChoreEligibleMemberRepository;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import gdg.sharinglog.repository.rotation.ChoreRepository;
import gdg.sharinglog.repository.rotation.OccurrenceEligibleMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OccurrenceGenerationService {

    private final ChoreRepository choreRepository;
    private final ChoreEligibleMemberRepository choreEligibleMemberRepository;
    private final ChoreOccurrenceRepository occurrenceRepository;
    private final OccurrenceEligibleMemberRepository occurrenceEligibleMemberRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ChoreOccurrenceScheduleResolver scheduleResolver;
    private final RotationAssignmentService assignmentService;

    public OccurrenceGenerationService(
            ChoreRepository choreRepository,
            ChoreEligibleMemberRepository choreEligibleMemberRepository,
            ChoreOccurrenceRepository occurrenceRepository,
            OccurrenceEligibleMemberRepository occurrenceEligibleMemberRepository,
            GroupMemberRepository groupMemberRepository,
            ChoreOccurrenceScheduleResolver scheduleResolver,
            RotationAssignmentService assignmentService
    ) {
        this.choreRepository = choreRepository;
        this.choreEligibleMemberRepository = choreEligibleMemberRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.occurrenceEligibleMemberRepository = occurrenceEligibleMemberRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.scheduleResolver = scheduleResolver;
        this.assignmentService = assignmentService;
    }

    @Transactional
    public ChoreOccurrence ensureCurrentOccurrence(Long choreId, Instant referenceInstant) {
        Objects.requireNonNull(choreId, "업무 ID는 필수입니다.");
        Objects.requireNonNull(referenceInstant, "기준 시각은 필수입니다.");
        Chore chore = choreRepository.findByIdForUpdate(choreId)
                .orElseThrow(() -> new ChoreNotFoundException(choreId));
        if (!chore.isActive()) {
            throw new IllegalStateException("비활성 업무의 새 회차는 생성할 수 없습니다.");
        }

        OccurrenceSchedule schedule = scheduleResolver.resolve(chore, referenceInstant);
        return occurrenceRepository
                .findByChore_IdAndPeriodStart(chore.getId(), schedule.periodStart())
                .orElseGet(() -> createAndAssign(chore, schedule, referenceInstant));
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

        List<GroupMember> eligibleMembers = eligibleMembers(chore);
        List<OccurrenceEligibleMember> snapshots = eligibleMembers.stream()
                .map(member -> new OccurrenceEligibleMember(
                        occurrence,
                        occurrence.getEligibilitySnapshotVersion(),
                        member,
                        createdAt
                ))
                .toList();
        occurrenceEligibleMemberRepository.saveAllAndFlush(snapshots);
        assignmentService.assign(occurrence, AssignmentTrigger.INITIAL, createdAt);
        return occurrence;
    }

    private List<GroupMember> eligibleMembers(Chore chore) {
        if (chore.getEligibilityMode() == ChoreEligibilityMode.ALL_ACTIVE_MEMBERS) {
            return groupMemberRepository.findAllByGroup_IdAndStatusOrderById(
                    chore.getGroup().getId(),
                    MemberStatus.ACTIVE
            );
        }
        return choreEligibleMemberRepository
                .findAllByChore_IdOrderById(chore.getId())
                .stream()
                .map(eligible -> eligible.getMember())
                .toList();
    }
}

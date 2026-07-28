package gdg.sharinglog.service.rotation.api.chore;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.MemberStatus;
import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreEligibilityMode;
import gdg.sharinglog.domain.rotation.ChoreFrequency;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.rotation.ChoreRepository;
import gdg.sharinglog.service.rotation.ChoreEnrollmentService;
import gdg.sharinglog.service.rotation.OccurrenceGenerationService;
import gdg.sharinglog.service.rotation.access.RotationActor;
import gdg.sharinglog.service.rotation.access.RotationActorAccessService;
import gdg.sharinglog.service.rotation.access.RotationMemberNotFoundException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChoreApplicationService {

    private final RotationActorAccessService accessService;
    private final GroupMemberRepository groupMemberRepository;
    private final ChoreRepository choreRepository;
    private final ChoreEnrollmentService enrollmentService;
    private final OccurrenceGenerationService occurrenceGenerationService;

    public ChoreApplicationService(
            RotationActorAccessService accessService,
            GroupMemberRepository groupMemberRepository,
            ChoreRepository choreRepository,
            ChoreEnrollmentService enrollmentService,
            OccurrenceGenerationService occurrenceGenerationService
    ) {
        this.accessService = accessService;
        this.groupMemberRepository = groupMemberRepository;
        this.choreRepository = choreRepository;
        this.enrollmentService = enrollmentService;
        this.occurrenceGenerationService = occurrenceGenerationService;
    }

    @Transactional
    public CreatedChore create(
            String groupPublicId,
            String registrationId,
            OAuth2User principal,
            CreateChoreCommand command,
            Instant createdAt
    ) {
        Objects.requireNonNull(command, "업무 생성 명령은 필수입니다.");
        Instant effectiveCreatedAt =
                Objects.requireNonNull(createdAt, "업무 생성 시각은 필수입니다.");
        RotationActor actor =
                accessService.requireOwnerForUpdate(groupPublicId, registrationId, principal);
        List<GroupMember> selectedMembers = resolveSelectedMembers(actor, command);
        Chore chore = choreRepository.saveAndFlush(createChore(actor, command, effectiveCreatedAt));
        enrollmentService.initializeChore(chore, selectedMembers, effectiveCreatedAt);

        var occurrence =
                occurrenceGenerationService.ensureCurrentOccurrence(chore.getId(), effectiveCreatedAt);
        return new CreatedChore(new ChoreView(chore, selectedMembers), occurrence, actor);
    }

    @Transactional(readOnly = true)
    public List<ChoreView> findAll(
            String groupPublicId,
            String registrationId,
            OAuth2User principal,
            ChoreFrequency frequency,
            Boolean active
    ) {
        RotationActor actor =
                accessService.requireActiveMember(groupPublicId, registrationId, principal);
        return choreRepository.findAllByGroup_IdOrderById(actor.group().getId()).stream()
                .filter(chore -> frequency == null || chore.getFrequency() == frequency)
                .filter(chore -> active == null || chore.isActive() == active)
                .map(chore -> new ChoreView(chore, currentEligibleMembers(chore)))
                .toList();
    }

    private Chore createChore(
            RotationActor actor,
            CreateChoreCommand command,
            Instant createdAt
    ) {
        return switch (Objects.requireNonNull(command.frequency(), "반복 주기는 필수입니다.")) {
            case DAILY -> Chore.daily(
                    actor.group(),
                    actor.membership(),
                    command.name(),
                    command.eligibilityMode(),
                    command.dueTime(),
                    createdAt
            );
            case WEEKLY -> Chore.weekly(
                    actor.group(),
                    actor.membership(),
                    command.name(),
                    command.eligibilityMode(),
                    command.weeklyDueDay(),
                    command.dueTime(),
                    createdAt
            );
            case BIWEEKLY -> Chore.biweekly(
                    actor.group(),
                    actor.membership(),
                    command.name(),
                    command.eligibilityMode(),
                    command.biweeklyAnchorDate(),
                    command.dueTime(),
                    createdAt
            );
        };
    }

    private List<GroupMember> resolveSelectedMembers(
            RotationActor actor,
            CreateChoreCommand command
    ) {
        ChoreEligibilityMode mode =
                Objects.requireNonNull(command.eligibilityMode(), "가능 멤버 모드는 필수입니다.");
        List<String> requested = command.eligibleMembershipPublicIds();
        if (mode == ChoreEligibilityMode.ALL_ACTIVE_MEMBERS) {
            if (!requested.isEmpty()) {
                throw new IllegalArgumentException("전체 멤버 모드에는 개별 멤버를 지정할 수 없습니다.");
            }
            return List.of();
        }

        Set<String> distinct = new HashSet<>(requested);
        if (requested.isEmpty() || distinct.size() != requested.size()) {
            throw new IllegalArgumentException("선택 멤버는 중복 없이 한 명 이상이어야 합니다.");
        }
        List<GroupMember> members =
                groupMemberRepository.findAllByGroup_IdAndPublicIdInAndStatusOrderById(
                        actor.group().getId(),
                        distinct,
                        MemberStatus.ACTIVE
                );
        Set<String> resolved = members.stream()
                .map(GroupMember::getPublicId)
                .collect(java.util.stream.Collectors.toSet());
        if (!resolved.equals(distinct)) {
            throw new RotationMemberNotFoundException(
                    distinct.stream().filter(id -> !resolved.contains(id)).findFirst().orElse("")
            );
        }
        return members;
    }

    private List<GroupMember> currentEligibleMembers(Chore chore) {
        return enrollmentService.findActiveMembers(chore);
    }
}

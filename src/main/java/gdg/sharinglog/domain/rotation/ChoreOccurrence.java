package gdg.sharinglog.domain.rotation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import gdg.sharinglog.domain.GroupMember;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(
        name = "chore_occurrences",
        indexes = {
                @Index(name = "idx_chore_occurrences_status_due", columnList = "status, due_at"),
                @Index(name = "idx_chore_occurrences_current_assignment", columnList = "current_assignment_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chore_occurrences_chore_period",
                columnNames = {"chore_id", "period_start"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class ChoreOccurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, unique = true, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "chore_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_chore_occurrences_chore")
    )
    private Chore chore;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency_snapshot", nullable = false, length = 20)
    private ChoreFrequency frequencySnapshot;

    @Column(name = "time_zone_id_snapshot", nullable = false, length = 40)
    private String timeZoneIdSnapshot;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end_exclusive", nullable = false)
    private LocalDate periodEndExclusive;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "eligibility_snapshot_version", nullable = false)
    private int eligibilitySnapshotVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OccurrenceStatus status;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "current_assignment_id",
            unique = true,
            foreignKey = @ForeignKey(name = "fk_chore_occurrences_current_assignment")
    )
    private ChoreAssignmentAttempt currentAssignment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private ChoreOccurrence(
            Chore chore,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            Instant dueAt,
            Instant createdAt
    ) {
        this.chore = Objects.requireNonNull(chore, "업무는 필수입니다.");
        this.frequencySnapshot = chore.getFrequency();
        this.timeZoneIdSnapshot = chore.getGroup().getTimeZoneId();
        this.periodStart = Objects.requireNonNull(periodStart, "기간 시작일은 필수입니다.");
        this.periodEndExclusive = Objects.requireNonNull(periodEndExclusive, "기간 종료일은 필수입니다.");
        if (!periodEndExclusive.isAfter(periodStart)) {
            throw new IllegalArgumentException("기간 종료일은 시작일보다 뒤여야 합니다.");
        }
        this.dueAt = Objects.requireNonNull(dueAt, "마감 시각은 필수입니다.");
        this.createdAt = Objects.requireNonNull(createdAt, "생성 시각은 필수입니다.");
        this.publicId = UUID.randomUUID().toString();
        this.eligibilitySnapshotVersion = 1;
        this.status = OccurrenceStatus.NEEDS_ATTENTION;
    }

    public static ChoreOccurrence create(
            Chore chore,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            Instant dueAt,
            Instant createdAt
    ) {
        return new ChoreOccurrence(chore, periodStart, periodEndExclusive, dueAt, createdAt);
    }

    public Optional<GroupMember> currentAssignee() {
        return Optional.ofNullable(currentAssignment).map(ChoreAssignmentAttempt::getAssignee);
    }

    public void assign(ChoreAssignmentAttempt assignment) {
        if (status != OccurrenceStatus.NEEDS_ATTENTION || currentAssignment != null) {
            throw new IllegalStateException("담당자가 없는 관리 필요 회차에만 배정할 수 있습니다.");
        }
        ChoreAssignmentAttempt required = Objects.requireNonNull(assignment, "배정 시도는 필수입니다.");
        if (required.getOccurrence() != this) {
            throw new IllegalArgumentException("배정 시도는 같은 회차에 속해야 합니다.");
        }
        if (!required.isActive()) {
            throw new IllegalArgumentException("이미 종료된 배정 시도는 현재 배정으로 지정할 수 없습니다.");
        }
        this.currentAssignment = required;
        this.status = OccurrenceStatus.ASSIGNED;
    }

    public void complete(Instant completedAt) {
        close(OccurrenceStatus.COMPLETED, AssignmentEndReason.COMPLETED, completedAt);
    }

    public void skipAlreadyDone(Instant skippedAt) {
        close(OccurrenceStatus.SKIPPED, AssignmentEndReason.SKIPPED_ALREADY_DONE, skippedAt);
    }

    public void releaseForReassignment(AssignmentEndReason reason, Instant endedAt) {
        if (!Objects.requireNonNull(reason, "배정 종료 사유는 필수입니다.").requiresReassignment()) {
            throw new IllegalArgumentException("재배정이 필요한 종료 사유만 사용할 수 있습니다.");
        }
        requireAssigned();
        currentAssignment.end(reason, endedAt);
        currentAssignment = null;
        status = OccurrenceStatus.NEEDS_ATTENTION;
    }

    public void advanceEligibilitySnapshotVersion() {
        if (status != OccurrenceStatus.NEEDS_ATTENTION) {
            throw new IllegalStateException("관리 필요 회차의 가능 멤버 조건만 갱신할 수 있습니다.");
        }
        eligibilitySnapshotVersion++;
    }

    private void close(OccurrenceStatus terminalStatus, AssignmentEndReason reason, Instant endedAt) {
        if (!terminalStatus.isTerminal()) {
            throw new IllegalArgumentException("종료 상태가 아닙니다.");
        }
        requireAssigned();
        Instant effectiveEndedAt = Objects.requireNonNull(endedAt, "종료 시각은 필수입니다.");
        currentAssignment.end(reason, effectiveEndedAt);
        currentAssignment = null;
        status = terminalStatus;
        closedAt = effectiveEndedAt;
    }

    private void requireAssigned() {
        if (status != OccurrenceStatus.ASSIGNED || currentAssignment == null) {
            throw new IllegalStateException("현재 담당자가 있는 배정 상태에서만 처리할 수 있습니다.");
        }
    }
}

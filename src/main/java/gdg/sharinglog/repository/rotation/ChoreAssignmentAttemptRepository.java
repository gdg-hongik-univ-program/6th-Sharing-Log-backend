package gdg.sharinglog.repository.rotation;

import java.util.List;
import java.util.Optional;

import gdg.sharinglog.domain.rotation.AssignmentEndReason;
import gdg.sharinglog.domain.rotation.ChoreAssignmentAttempt;
import gdg.sharinglog.domain.rotation.ChoreFrequency;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChoreAssignmentAttemptRepository
        extends JpaRepository<ChoreAssignmentAttempt, Long> {

    @EntityGraph(attributePaths = "assignee")
    List<ChoreAssignmentAttempt> findAllByOccurrence_IdOrderBySequenceNumber(Long occurrenceId);

    @EntityGraph(attributePaths = "assignee")
    Optional<ChoreAssignmentAttempt> findFirstByOccurrence_IdAndEndedAtIsNull(Long occurrenceId);

    @EntityGraph(attributePaths = {"assignee", "assignee.user"})
    Optional<ChoreAssignmentAttempt>
    findFirstByOccurrence_IdOrderBySequenceNumberDesc(Long occurrenceId);

    long countByOccurrence_Id(Long occurrenceId);

    boolean existsByOccurrence_IdAndAssignee_IdAndEndReason(
            Long occurrenceId,
            Long assigneeId,
            AssignmentEndReason endReason
    );

    @Query("""
            select count(attempt)
            from ChoreAssignmentAttempt attempt
            where attempt.occurrence.chore.id = :choreId
              and attempt.assignee.id = :membershipId
              and attempt.endReason = gdg.sharinglog.domain.rotation.AssignmentEndReason.COMPLETED
            """)
    long countCompletedForChoreAndMember(
            @Param("choreId") Long choreId,
            @Param("membershipId") Long membershipId
    );

    @Query("""
            select count(attempt)
            from ChoreAssignmentAttempt attempt
            where attempt.occurrence.chore.group.id = :groupId
              and attempt.occurrence.frequencySnapshot = :frequency
              and attempt.occurrence.periodStart = :periodStart
              and attempt.assignee.id = :membershipId
              and (
                    attempt.endReason is null
                    or attempt.endReason =
                       gdg.sharinglog.domain.rotation.AssignmentEndReason.COMPLETED
              )
            """)
    long countActiveOrCompletedPeriodLoad(
            @Param("groupId") Long groupId,
            @Param("frequency") ChoreFrequency frequency,
            @Param("periodStart") java.time.LocalDate periodStart,
            @Param("membershipId") Long membershipId
    );
}

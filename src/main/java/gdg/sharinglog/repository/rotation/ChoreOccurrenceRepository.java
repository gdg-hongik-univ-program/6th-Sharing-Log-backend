package gdg.sharinglog.repository.rotation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChoreOccurrenceRepository extends JpaRepository<ChoreOccurrence, Long> {

    @EntityGraph(attributePaths = {"chore", "currentAssignment", "currentAssignment.assignee"})
    Optional<ChoreOccurrence> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"chore", "currentAssignment", "currentAssignment.assignee"})
    @Query("select occurrence from ChoreOccurrence occurrence where occurrence.publicId = :publicId")
    Optional<ChoreOccurrence> findByPublicIdForUpdate(@Param("publicId") String publicId);

    Optional<ChoreOccurrence> findByChore_IdAndPeriodStart(Long choreId, LocalDate periodStart);

    Optional<ChoreOccurrence>
    findFirstByChore_IdAndPeriodStartBeforeOrderByPeriodStartDesc(
            Long choreId,
            LocalDate periodStart
    );

    @EntityGraph(attributePaths = {"chore", "currentAssignment", "currentAssignment.assignee"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ChoreOccurrence> findAllByCurrentAssignment_Assignee_IdAndStatus(
            Long membershipId,
            OccurrenceStatus status
    );

    @EntityGraph(attributePaths = {"chore", "currentAssignment", "currentAssignment.assignee"})
    List<ChoreOccurrence> findAllByChore_Group_IdAndPeriodStartBetweenOrderByPeriodStartAscIdAsc(
            Long groupId,
            LocalDate fromInclusive,
            LocalDate toInclusive
    );
}

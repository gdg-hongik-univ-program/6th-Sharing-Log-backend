package gdg.sharinglog.repository.rotation;

import java.util.List;
import java.util.Optional;

import gdg.sharinglog.domain.rotation.Chore;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChoreRepository extends JpaRepository<Chore, Long> {

    Optional<Chore> findByPublicIdAndGroup_Id(String publicId, Long groupId);

    List<Chore> findAllByGroup_IdAndActiveTrueOrderById(Long groupId);

    @Query("select chore.group.id from Chore chore where chore.id = :choreId")
    Optional<Long> findGroupIdById(@Param("choreId") Long choreId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "group")
    @Query("select chore from Chore chore where chore.id = :choreId")
    Optional<Chore> findByIdForUpdate(@Param("choreId") Long choreId);
}

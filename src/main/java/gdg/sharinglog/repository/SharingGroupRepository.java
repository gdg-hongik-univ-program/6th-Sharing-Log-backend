package gdg.sharinglog.repository;

import java.util.Optional;

import gdg.sharinglog.domain.SharingGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SharingGroupRepository extends JpaRepository<SharingGroup, Long> {

    Optional<SharingGroup> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select sharingGroup
            from SharingGroup sharingGroup
            where sharingGroup.publicId = :publicId
            """)
    Optional<SharingGroup> findByPublicIdForUpdate(@Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sharingGroup from SharingGroup sharingGroup where sharingGroup.id = :groupId")
    Optional<SharingGroup> findByIdForUpdate(@Param("groupId") Long groupId);
}

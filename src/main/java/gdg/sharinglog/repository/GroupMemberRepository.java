package gdg.sharinglog.repository;

import java.util.List;
import java.util.Optional;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.MemberStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    Optional<GroupMember> findByGroup_IdAndUser_Id(Long groupId, Long userId);

    Optional<GroupMember> findByGroup_IdAndUser_IdAndStatus(
            Long groupId,
            Long userId,
            MemberStatus status
    );

    @EntityGraph(attributePaths = "user")
    List<GroupMember> findAllByGroup_Id(Long groupId);

    @EntityGraph(attributePaths = "user")
    List<GroupMember> findAllByGroup_IdAndStatusOrderById(Long groupId, MemberStatus status);

    @Query("select member.group.id from GroupMember member where member.publicId = :publicId")
    Optional<Long> findGroupIdByPublicId(@Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select member from GroupMember member where member.publicId = :publicId")
    Optional<GroupMember> findByPublicIdForUpdate(@Param("publicId") String publicId);
}

package gdg.sharinglog.repository;

import java.util.List;
import java.util.Optional;

import gdg.sharinglog.domain.GroupMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    Optional<GroupMember> findByGroup_IdAndUser_Id(Long groupId, Long userId);

    @EntityGraph(attributePaths = "user")
    List<GroupMember> findAllByGroup_Id(Long groupId);
}

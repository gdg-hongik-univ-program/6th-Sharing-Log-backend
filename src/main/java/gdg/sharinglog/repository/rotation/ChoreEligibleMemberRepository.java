package gdg.sharinglog.repository.rotation;

import java.util.List;

import gdg.sharinglog.domain.rotation.ChoreEligibleMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChoreEligibleMemberRepository extends JpaRepository<ChoreEligibleMember, Long> {

    @EntityGraph(attributePaths = "member")
    List<ChoreEligibleMember> findAllByChore_IdOrderById(Long choreId);
}

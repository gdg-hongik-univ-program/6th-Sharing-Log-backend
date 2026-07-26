package gdg.sharinglog.repository;

import gdg.sharinglog.domain.SharingGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharingGroupRepository extends JpaRepository<SharingGroup, Long> {
}

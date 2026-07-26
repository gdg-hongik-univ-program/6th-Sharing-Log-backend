package gdg.sharinglog.domain;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(name = "sharing_groups")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class SharingGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "created_by_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_sharing_groups_created_by_user")
    )
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public SharingGroup(String name, User createdBy) {
        this.name = Objects.requireNonNull(name, "그룹 이름은 필수입니다.");
        this.createdBy = Objects.requireNonNull(createdBy, "그룹 생성자는 필수입니다.");
        this.createdAt = Instant.now();
    }
}

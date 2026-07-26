package gdg.sharinglog.domain.rotation;

import java.util.Objects;

import gdg.sharinglog.domain.GroupMember;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(
        name = "chore_eligible_members",
        indexes = @Index(name = "idx_chore_eligible_members_member", columnList = "membership_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chore_eligible_members_chore_member",
                columnNames = {"chore_id", "membership_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class ChoreEligibleMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "chore_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_chore_eligible_members_chore")
    )
    private Chore chore;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "membership_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_chore_eligible_members_membership")
    )
    private GroupMember member;

    public ChoreEligibleMember(Chore chore, GroupMember member) {
        this.chore = Objects.requireNonNull(chore, "업무는 필수입니다.");
        this.member = Objects.requireNonNull(member, "가능 멤버는 필수입니다.");
        if (chore.getEligibilityMode() != ChoreEligibilityMode.SELECTED_MEMBERS) {
            throw new IllegalArgumentException("지정 멤버 방식의 업무에만 가능 멤버를 추가할 수 있습니다.");
        }
        if (member.getGroup() != chore.getGroup()) {
            throw new IllegalArgumentException("가능 멤버는 업무와 같은 그룹에 속해야 합니다.");
        }
    }
}

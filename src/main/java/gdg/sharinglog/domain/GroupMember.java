package gdg.sharinglog.domain;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "group_members",
        indexes = @Index(name = "idx_group_members_user_id", columnList = "user_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_group_members_group_user",
                columnNames = {"group_id", "user_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "group_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_group_members_group")
    )
    private SharingGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_group_members_user")
    )
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private GroupRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    private GroupMember(SharingGroup group, User user, GroupRole role) {
        this.group = Objects.requireNonNull(group, "그룹은 필수입니다.");
        this.user = Objects.requireNonNull(user, "사용자는 필수입니다.");
        this.role = Objects.requireNonNull(role, "그룹 역할은 필수입니다.");
        this.joinedAt = Instant.now();
    }

    public static GroupMember owner(SharingGroup group, User user) {
        return new GroupMember(group, user, GroupRole.OWNER);
    }

    public static GroupMember member(SharingGroup group, User user) {
        return new GroupMember(group, user, GroupRole.MEMBER);
    }
}

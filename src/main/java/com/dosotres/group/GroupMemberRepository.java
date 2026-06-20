package com.dosotres.group;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    List<GroupMember> findByUserId(Long userId);

    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    List<GroupMember> findByGroupId(Long groupId);

    @Query("SELECT gm.user.id FROM GroupMember gm WHERE gm.group.id = :groupId")
    List<Long> findUserIdsByGroupId(@Param("groupId") Long groupId);

    /** ¿Comparten al menos un grupo dos usuarios? (mensajería Fase A). */
    @Query("SELECT COUNT(m1) > 0 FROM GroupMember m1, GroupMember m2 "
            + "WHERE m1.group.id = m2.group.id AND m1.user.id = :a AND m2.user.id = :b")
    boolean existsSharedGroup(@Param("a") Long a, @Param("b") Long b);
}

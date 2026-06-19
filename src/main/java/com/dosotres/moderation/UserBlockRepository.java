package com.dosotres.moderation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    Optional<UserBlock> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    List<UserBlock> findByBlockerIdOrderByCreatedAtDesc(Long blockerId);

    /**
     * ¿Existe un bloqueo en cualquier dirección entre dos usuarios?
     * Cimiento para mensajería (Fase 3): si hay bloqueo, no se permite el contacto.
     */
    @Query("select count(b) > 0 from UserBlock b where "
            + "(b.blocker.id = :a and b.blocked.id = :b) or "
            + "(b.blocker.id = :b and b.blocked.id = :a)")
    boolean existsBlockBetween(@Param("a") Long a, @Param("b") Long b);
}

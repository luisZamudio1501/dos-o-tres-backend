package com.dosotres.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /** Filtra, de la lista de candidatos, quiénes quieren avisos de pedido creado. */
    List<Long> findIdByIdInAndNotifyOnRequestCreatedTrue(List<Long> ids);

    /** Filtra, de la lista de candidatos, quiénes quieren avisos de "alguien oró". */
    List<Long> findIdByIdInAndNotifyOnPrayedTrue(List<Long> ids);

    /** Filtra, de la lista de candidatos, quiénes quieren avisos de pedido respondido. */
    List<Long> findIdByIdInAndNotifyOnAnsweredTrue(List<Long> ids);

    /**
     * Marca actividad "abrió la app" con throttle en SQL: solo reescribe si nunca
     * se vio o si el último valor es anterior a {@code threshold} (now - 1h). Update
     * atómico, sin cargar la entidad ni disparar {@code @PreUpdate} (no toca updated_at).
     * Devuelve la cantidad de filas afectadas (0 si el throttle lo evitó).
     */
    @Modifying
    @Query("UPDATE User u SET u.lastSeenAt = :now "
            + "WHERE u.id = :id AND (u.lastSeenAt IS NULL OR u.lastSeenAt < :threshold)")
    int touchLastSeen(@Param("id") Long id,
                      @Param("now") Instant now,
                      @Param("threshold") Instant threshold);

    /** Búsqueda paginada del CRM de admin por email o nombre (case-insensitive). */
    @Query("SELECT u FROM User u WHERE "
            + "LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<User> searchForAdmin(@Param("q") String q, Pageable pageable);
}

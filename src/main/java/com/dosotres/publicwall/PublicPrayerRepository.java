package com.dosotres.publicwall;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicPrayerRepository extends JpaRepository<PublicPrayer, Long> {

    boolean existsByRequestIdAndUserId(Long requestId, Long userId);

    /** Quiénes oraron por un pedido, más reciente primero (para que el autor los vea / agradezca). */
    List<PublicPrayer> findByRequestIdOrderByPrayedAtDesc(Long requestId);

    void deleteByRequestId(Long requestId);

    /** IDs de pedidos (entre los dados) por los que el usuario ya oró — evita N+1 en el feed. */
    @Query("select p.request.id from PublicPrayer p "
            + "where p.user.id = :userId and p.request.id in :requestIds")
    List<Long> findPrayedRequestIds(@Param("userId") Long userId,
                                    @Param("requestIds") List<Long> requestIds);
}

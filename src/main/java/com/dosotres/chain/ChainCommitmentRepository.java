package com.dosotres.chain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChainCommitmentRepository extends JpaRepository<ChainCommitment, Long> {

    List<ChainCommitment> findByChainId(Long chainId);

    Optional<ChainCommitment> findByChainIdAndUserIdAndSlotIndex(Long chainId, Long userId, int slotIndex);

    void deleteByChainId(Long chainId);

    @Query("select count(distinct c.slotIndex) from ChainCommitment c where c.chain.id = :chainId")
    long countCoveredSlots(@Param("chainId") Long chainId);
}

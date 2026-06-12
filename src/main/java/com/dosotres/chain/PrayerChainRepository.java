package com.dosotres.chain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrayerChainRepository extends JpaRepository<PrayerChain, Long> {

    List<PrayerChain> findByGroupIdOrderByDateFromDesc(Long groupId);
}

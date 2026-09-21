package com.turkishtechnology.tierstatus.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerRateRepository extends JpaRepository<PartnerRateEntity, String> {

    @Query("select rate from PartnerRateEntity rate where " +
            "rate.partnerCode = :partnerCode and rate.currency = :currency " +
            "and rate.effectiveFrom <= :activityDate " +
            "and (rate.effectiveTo is null or rate.effectiveTo > :activityDate) " +
            "order by rate.effectiveFrom desc")
    List<PartnerRateEntity> findApplicable(@Param("partnerCode") String partnerCode,
                                            @Param("currency") String currency,
                                            @Param("activityDate") LocalDate activityDate,
                                            Pageable pageable);
}

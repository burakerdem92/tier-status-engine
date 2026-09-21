package com.turkishtechnology.tierstatus.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlightRateRepository extends JpaRepository<FlightRateEntity, String> {

    @Query("select rate from FlightRateEntity rate where " +
            "rate.operatingCarrier = :carrier and rate.bookingClass = :bookingClass " +
            "and rate.effectiveFrom <= :activityDate " +
            "and (rate.effectiveTo is null or rate.effectiveTo > :activityDate) " +
            "order by rate.effectiveFrom desc")
    List<FlightRateEntity> findApplicable(@Param("carrier") String carrier,
                                           @Param("bookingClass") String bookingClass,
                                           @Param("activityDate") LocalDate activityDate,
                                           Pageable pageable);
}

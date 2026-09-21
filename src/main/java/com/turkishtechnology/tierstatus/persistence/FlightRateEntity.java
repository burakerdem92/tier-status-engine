package com.turkishtechnology.tierstatus.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "flight_rate", schema = "reference_data")
public class FlightRateEntity {

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "operating_carrier", nullable = false, length = 10)
    private String operatingCarrier;
    @Column(name = "booking_class", nullable = false, length = 10)
    private String bookingClass;
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal rate;
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;
    @Column(name = "effective_to")
    private LocalDate effectiveTo;
    @Column(name = "rule_version", nullable = false, length = 80)
    private String ruleVersion;

    protected FlightRateEntity() {
    }

    public BigDecimal getRate() { return rate; }
    public String getRuleVersion() { return ruleVersion; }
}

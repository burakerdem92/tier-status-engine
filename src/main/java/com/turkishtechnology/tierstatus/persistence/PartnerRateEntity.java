package com.turkishtechnology.tierstatus.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "partner_rate", schema = "reference_data")
public class PartnerRateEntity {

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "partner_code", nullable = false, length = 80)
    private String partnerCode;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal rate;
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;
    @Column(name = "effective_to")
    private LocalDate effectiveTo;
    @Column(name = "rule_version", nullable = false, length = 80)
    private String ruleVersion;

    protected PartnerRateEntity() {
    }

    public BigDecimal getRate() { return rate; }
    public String getRuleVersion() { return ruleVersion; }
}

package com.turkishtechnology.tierstatus.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "member_period", uniqueConstraints =
        @UniqueConstraint(name = "uq_member_period", columnNames = {"member_id", "period_year"}))
public class MemberPeriodEntity extends UuidEntity {

    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "total_status_miles", nullable = false)
    private int totalStatusMiles;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MemberPeriodEntity() {
    }

    public MemberPeriodEntity(String memberId, int periodYear, Instant createdAt) {
        this.memberId = memberId;
        this.periodYear = periodYear;
        this.updatedAt = createdAt;
    }

    public void addMiles(int miles, Instant at) {
        totalStatusMiles = Math.addExact(totalStatusMiles, miles);
        updatedAt = at;
    }

    public String getMemberId() { return memberId; }
    public int getPeriodYear() { return periodYear; }
    public int getTotalStatusMiles() { return totalStatusMiles; }
}

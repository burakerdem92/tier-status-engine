package com.turkishtechnology.tierstatus.persistence;

import com.turkishtechnology.tierstatus.domain.Tier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "member_status")
public class MemberStatusEntity {

    @Id
    @Column(name = "member_id", length = 64)
    private String memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Tier tier;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MemberStatusEntity() {
    }

    public MemberStatusEntity(String memberId, Instant createdAt) {
        this.memberId = memberId;
        this.tier = Tier.CLASSIC;
        this.updatedAt = createdAt;
    }

    public void changeTo(Tier newTier, LocalDate newValidUntil, Instant at) {
        tier = newTier;
        validUntil = newValidUntil;
        updatedAt = at;
    }

    public String getMemberId() { return memberId; }
    public Tier getTier() { return tier; }
    public LocalDate getValidUntil() { return validUntil; }
}

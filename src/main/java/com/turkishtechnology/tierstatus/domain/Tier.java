package com.turkishtechnology.tierstatus.domain;

import java.util.Arrays;

public enum Tier {
    CLASSIC(0, 0, 0),
    CLASSIC_PLUS(1, 25_000, 15_000),
    ELITE(2, 40_000, 30_000),
    ELITE_PLUS(3, 80_000, 60_000);

    private final int rank;
    private final int qualificationThreshold;
    private final int renewalThreshold;

    Tier(int rank, int qualificationThreshold, int renewalThreshold) {
        this.rank = rank;
        this.qualificationThreshold = qualificationThreshold;
        this.renewalThreshold = renewalThreshold;
    }

    public int rank() {
        return rank;
    }

    public int qualificationThreshold() {
        return qualificationThreshold;
    }

    public int renewalThreshold() {
        return renewalThreshold;
    }

    public Tier lower() {
        return switch (this) {
            case ELITE_PLUS -> ELITE;
            case ELITE -> CLASSIC_PLUS;
            case CLASSIC_PLUS, CLASSIC -> CLASSIC;
        };
    }

    public Tier next() {
        return switch (this) {
            case CLASSIC -> CLASSIC_PLUS;
            case CLASSIC_PLUS -> ELITE;
            case ELITE -> ELITE_PLUS;
            case ELITE_PLUS -> null;
        };
    }

    public static Tier highestQualified(int statusMiles) {
        return Arrays.stream(values())
                .filter(tier -> statusMiles >= tier.qualificationThreshold)
                .max((left, right) -> Integer.compare(left.rank, right.rank))
                .orElse(CLASSIC);
    }
}

package com.turkishtechnology.tierstatus.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TierTest {

    @ParameterizedTest
    @CsvSource({
            "0, CLASSIC",
            "24999, CLASSIC",
            "25000, CLASSIC_PLUS",
            "39999, CLASSIC_PLUS",
            "40000, ELITE",
            "79999, ELITE",
            "80000, ELITE_PLUS"
    })
    void returnsHighestQualifiedTier(int miles, Tier expected) {
        assertThat(Tier.highestQualified(miles)).isEqualTo(expected);
    }
}

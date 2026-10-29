package com.talentlens.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LikePatternTests {

    @Test
    void wrapsTrimmedLowercaseTermInWildcards() {
        assertThat(LikePattern.contains("  Bengaluru ")).isEqualTo("%bengaluru%");
    }

    @Test
    void escapesWildcardsAndTheEscapeCharacter() {
        assertThat(LikePattern.contains("100%")).isEqualTo("%100\\%%");
        assertThat(LikePattern.contains("dev_ops")).isEqualTo("%dev\\_ops%");
        assertThat(LikePattern.contains("a\\b")).isEqualTo("%a\\\\b%");
    }
}

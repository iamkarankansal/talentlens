package com.talentlens.matching;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SkillGapNormalisationTests {

    @Test
    void ignoresCaseSpacesHyphensUnderscoresAndDots() {
        assertThat(SkillGapService.normalise("Spring Boot")).isEqualTo("springboot");
        assertThat(SkillGapService.normalise(" spring-boot ")).isEqualTo("springboot");
        assertThat(SkillGapService.normalise("SpringBoot")).isEqualTo("springboot");
        assertThat(SkillGapService.normalise("Node.js")).isEqualTo(SkillGapService.normalise("NodeJS"));
        assertThat(SkillGapService.normalise("CI_CD")).isEqualTo(SkillGapService.normalise("ci cd"));
    }

    @Test
    void keepsSymbolsThatDistinguishLanguages() {
        assertThat(SkillGapService.normalise("C++")).isEqualTo("c++");
        assertThat(SkillGapService.normalise("C#")).isEqualTo("c#");
        assertThat(SkillGapService.normalise("C")).isNotEqualTo(SkillGapService.normalise("C++"));
    }

    @Test
    void reducesSeparatorOnlyValuesToEmpty() {
        assertThat(SkillGapService.normalise(" - . ")).isEmpty();
    }
}

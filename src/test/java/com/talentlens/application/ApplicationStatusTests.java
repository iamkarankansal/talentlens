package com.talentlens.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApplicationStatusTests {

    @Test
    void movesForwardOneStageAtATime() {
        assertThat(ApplicationStatus.APPLIED.canMoveTo(ApplicationStatus.SCREENING)).isTrue();
        assertThat(ApplicationStatus.SCREENING.canMoveTo(ApplicationStatus.INTERVIEW)).isTrue();
        assertThat(ApplicationStatus.INTERVIEW.canMoveTo(ApplicationStatus.OFFER)).isTrue();
        assertThat(ApplicationStatus.OFFER.canMoveTo(ApplicationStatus.HIRED)).isTrue();

        assertThat(ApplicationStatus.APPLIED.canMoveTo(ApplicationStatus.INTERVIEW)).isFalse();
        assertThat(ApplicationStatus.INTERVIEW.canMoveTo(ApplicationStatus.SCREENING)).isFalse();
        assertThat(ApplicationStatus.APPLIED.canMoveTo(ApplicationStatus.APPLIED)).isFalse();
    }

    @Test
    void anyOpenApplicationCanBeRejectedOrWithdrawn() {
        for (ApplicationStatus status : ApplicationStatus.values()) {
            if (!status.isFinal()) {
                assertThat(status.canMoveTo(ApplicationStatus.REJECTED)).isTrue();
                assertThat(status.canMoveTo(ApplicationStatus.WITHDRAWN)).isTrue();
            }
        }
    }

    @Test
    void finalStagesCannotChange() {
        for (ApplicationStatus from : new ApplicationStatus[] {
                ApplicationStatus.HIRED, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN}) {
            for (ApplicationStatus to : ApplicationStatus.values()) {
                assertThat(from.canMoveTo(to)).isFalse();
            }
        }
    }
}

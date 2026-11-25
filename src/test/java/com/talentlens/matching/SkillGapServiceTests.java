package com.talentlens.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.common.ConflictException;
import com.talentlens.common.ResourceNotFoundException;
import com.talentlens.job.Job;
import com.talentlens.job.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SkillGapServiceTests {

    private static final Long JOB_ID = 1L;
    private static final Long CANDIDATE_ID = 7L;

    private JobService jobService;
    private CandidateRepository candidateRepository;
    private RequiredSkillsCache requiredSkillsCache;
    private SkillGapService service;
    private Job job;

    @BeforeEach
    void setUp() {
        jobService = mock(JobService.class);
        candidateRepository = mock(CandidateRepository.class);
        requiredSkillsCache = mock(RequiredSkillsCache.class);
        service = new SkillGapService(jobService, candidateRepository, requiredSkillsCache);

        job = new Job();
        job.setTitle("Backend Engineer");
        job.setDescription("Java services");
        job.setMinExperienceYears(3);
        when(jobService.getOrThrow(JOB_ID)).thenReturn(job);
    }

    @Test
    void splitsSkillsIntoMatchedMissingAndAdditional() {
        givenCandidate(parsedCandidate(4.5, List.of("java", "SpringBoot", "Python")));
        when(requiredSkillsCache.requiredSkills(job)).thenReturn(List.of("Java", "Spring Boot", "Kubernetes"));

        SkillGapResponse report = service.report(JOB_ID, CANDIDATE_ID);

        assertThat(report.jobId()).isEqualTo(JOB_ID);
        assertThat(report.candidateId()).isEqualTo(CANDIDATE_ID);
        assertThat(report.fullName()).isEqualTo("Asha Rao");
        assertThat(report.requiredSkills()).containsExactly("Java", "Spring Boot", "Kubernetes");
        assertThat(report.matchedSkills()).containsExactly("Java", "Spring Boot");
        assertThat(report.missingSkills()).containsExactly("Kubernetes");
        assertThat(report.additionalSkills()).containsExactly("Python");
        assertThat(report.coverage()).isEqualTo(0.6667);
        assertThat(report.requiredExperienceYears()).isEqualTo(3);
        assertThat(report.candidateExperienceYears()).isEqualTo(4.5);
        assertThat(report.meetsExperience()).isTrue();
    }

    @Test
    void dropsDuplicateBlankAndNullRequiredSkills() {
        givenCandidate(parsedCandidate(2.0, List.of("Java")));
        when(requiredSkillsCache.requiredSkills(job))
                .thenReturn(Arrays.asList(" Java ", "JAVA", "", null, " - ", "C++", "C"));

        SkillGapResponse report = service.report(JOB_ID, CANDIDATE_ID);

        assertThat(report.requiredSkills()).containsExactly("Java", "C++", "C");
        assertThat(report.matchedSkills()).containsExactly("Java");
        assertThat(report.missingSkills()).containsExactly("C++", "C");
        assertThat(report.coverage()).isEqualTo(0.3333);
        assertThat(report.meetsExperience()).isFalse();
    }

    @Test
    void leavesCoverageAndExperienceUnknownWhenThereIsNothingToCompare() {
        job.setMinExperienceYears(null);
        givenCandidate(parsedCandidate(null, List.of("Go")));
        when(requiredSkillsCache.requiredSkills(job)).thenReturn(List.of());

        SkillGapResponse report = service.report(JOB_ID, CANDIDATE_ID);

        assertThat(report.requiredSkills()).isEmpty();
        assertThat(report.coverage()).isNull();
        assertThat(report.additionalSkills()).containsExactly("Go");
        assertThat(report.meetsExperience()).isNull();
    }

    @Test
    void refusesCandidatesWithoutAParsedProfileBeforeAskingForSkills() {
        Candidate candidate = new Candidate();
        candidate.setFullName("Asha Rao");
        ReflectionTestUtils.setField(candidate, "id", CANDIDATE_ID);
        givenCandidate(candidate);

        assertThatThrownBy(() -> service.report(JOB_ID, CANDIDATE_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no parsed profile");
        verifyNoInteractions(requiredSkillsCache);
    }

    @Test
    void reportsAMissingCandidate() {
        when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.report(JOB_ID, CANDIDATE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(requiredSkillsCache);
    }

    private void givenCandidate(Candidate candidate) {
        when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
    }

    private static Candidate parsedCandidate(Double years, List<String> skills) {
        Candidate candidate = new Candidate();
        candidate.setFullName("Asha Rao");
        candidate.setEmail("asha@example.com");
        candidate.setProfile("Engineer", years, skills, Instant.parse("2026-11-01T10:00:00Z"));
        ReflectionTestUtils.setField(candidate, "id", CANDIDATE_ID);
        return candidate;
    }
}

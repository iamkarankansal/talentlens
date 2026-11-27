package com.talentlens.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.job.JobRepository;
import org.junit.jupiter.api.Test;

class DemoDataLoaderTests {

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final CandidateRepository candidateRepository = mock(CandidateRepository.class);
    private final DemoDataLoader loader = new DemoDataLoader(jobRepository, candidateRepository);

    @Test
    void seedsAnEmptyDatabase() {
        when(jobRepository.count()).thenReturn(0L);
        when(candidateRepository.count()).thenReturn(0L);

        assertThat(loader.seed()).isTrue();

        verify(jobRepository).saveAll(any());
        verify(candidateRepository).saveAll(any());
    }

    @Test
    void leavesADatabaseWithJobsAlone() {
        when(jobRepository.count()).thenReturn(1L);
        when(candidateRepository.count()).thenReturn(0L);

        assertThat(loader.seed()).isFalse();

        verify(jobRepository, never()).saveAll(any());
        verify(candidateRepository, never()).saveAll(any());
    }

    @Test
    void leavesADatabaseWithCandidatesAlone() {
        when(jobRepository.count()).thenReturn(0L);
        when(candidateRepository.count()).thenReturn(3L);

        assertThat(loader.seed()).isFalse();

        verify(jobRepository, never()).saveAll(any());
        verify(candidateRepository, never()).saveAll(any());
    }

    @Test
    void demoCandidatesHaveDistinctEmailsAndParsedSkills() {
        List<Candidate> candidates = DemoDataLoader.demoCandidates();
        Set<String> emails = new HashSet<>();
        for (Candidate candidate : candidates) {
            assertThat(emails.add(candidate.getEmail())).isTrue();
            assertThat(candidate.getSkills()).isNotEmpty();
            assertThat(candidate.getProfileParsedAt()).isNotNull();
        }
    }
}

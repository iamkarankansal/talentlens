package com.talentlens.assistant;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.common.ConflictException;
import com.talentlens.job.Job;
import com.talentlens.job.JobService;
import com.talentlens.matching.EmbeddingService;
import com.talentlens.matching.EmbeddingStore;
import com.talentlens.matching.EmbeddingTarget;
import com.talentlens.matching.MatchingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the tool-calling shortlisting agent for a job and keeps only picks that are real candidates,
 * without duplicates and within the requested size.
 */
@Service
@Transactional(readOnly = true)
public class ShortlistService {

    static final int DEFAULT_SIZE = 5;

    private final JobService jobService;
    private final CandidateRepository candidateRepository;
    private final MatchingService matchingService;
    private final EmbeddingStore embeddingStore;
    private final ShortlistAgent agent;

    public ShortlistService(JobService jobService, CandidateRepository candidateRepository,
                            MatchingService matchingService, EmbeddingStore embeddingStore, ShortlistAgent agent) {
        this.jobService = jobService;
        this.candidateRepository = candidateRepository;
        this.matchingService = matchingService;
        this.embeddingStore = embeddingStore;
        this.agent = agent;
    }

    public ShortlistResponse shortlist(Long jobId, int size) {
        Job job = jobService.getOrThrow(jobId);
        if (embeddingStore.find(EmbeddingTarget.JOB, jobId).isEmpty()) {
            throw new ConflictException(
                    "Job " + jobId + " has no embedding yet; POST /api/v1/jobs/" + jobId + "/embedding first");
        }

        ShortlistTools tools = new ShortlistTools(jobId, matchingService, candidateRepository);
        Shortlist result = agent.shortlist(EmbeddingService.jobText(job), size, tools);

        List<ShortlistResponse.Entry> entries = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        List<Shortlist.Pick> picks = result == null || result.candidates() == null ? List.of() : result.candidates();
        for (Shortlist.Pick pick : picks) {
            if (entries.size() == size) {
                break;
            }
            if (pick == null || pick.candidateId() == null || !seen.add(pick.candidateId())) {
                continue;
            }
            Candidate candidate = candidateRepository.findById(pick.candidateId()).orElse(null);
            if (candidate != null) {
                entries.add(new ShortlistResponse.Entry(entries.size() + 1, candidate.getId(),
                        candidate.getFullName(), candidate.getHeadline(), tools.scoreOf(candidate.getId()),
                        pick.reason()));
            }
        }
        return new ShortlistResponse(jobId, job.getTitle(), result == null ? null : result.summary(), entries,
                tools.calls());
    }
}

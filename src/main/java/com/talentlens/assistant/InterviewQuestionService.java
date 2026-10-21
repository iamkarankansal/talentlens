package com.talentlens.assistant;

import java.util.List;

import com.talentlens.ai.InterviewQuestionGenerator;
import com.talentlens.ai.InterviewQuestions;
import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.common.ConflictException;
import com.talentlens.common.ResourceNotFoundException;
import com.talentlens.job.Job;
import com.talentlens.job.JobService;
import com.talentlens.matching.EmbeddingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asks the chat model for interview questions tailored to one candidate and one job, from the same job and
 * candidate texts used for embeddings and match explanations.
 */
@Service
@Transactional(readOnly = true)
public class InterviewQuestionService {

    static final int DEFAULT_COUNT = 5;

    private final JobService jobService;
    private final CandidateRepository candidateRepository;
    private final InterviewQuestionGenerator generator;

    public InterviewQuestionService(JobService jobService, CandidateRepository candidateRepository,
                                    InterviewQuestionGenerator generator) {
        this.jobService = jobService;
        this.candidateRepository = candidateRepository;
        this.generator = generator;
    }

    public InterviewQuestionsResponse generate(Long jobId, Long candidateId, int count) {
        Job job = jobService.getOrThrow(jobId);
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate", candidateId));
        String candidateText = EmbeddingService.candidateText(candidate);
        if (candidateText.isBlank()) {
            throw new ConflictException("Candidate " + candidateId + " has no resume or profile to prepare from");
        }

        InterviewQuestions generated = generator.generate(EmbeddingService.jobText(job), candidateText, count);
        List<InterviewQuestions.Question> questions = generated == null || generated.questions() == null
                ? List.of()
                : generated.questions().stream()
                        .filter(q -> q != null && q.question() != null && !q.question().isBlank())
                        .limit(count)
                        .toList();
        return new InterviewQuestionsResponse(jobId, candidateId, candidate.getFullName(), job.getTitle(),
                questions);
    }
}

package com.talentlens.assistant;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Recruiter assistant",
        description = "Questions answered from the candidate pool, tailored interview questions and agent shortlists")
public class AssistantController {

    private final CandidatePoolChatService service;
    private final InterviewQuestionService interviewQuestionService;
    private final ShortlistService shortlistService;

    public AssistantController(CandidatePoolChatService service, InterviewQuestionService interviewQuestionService,
                               ShortlistService shortlistService) {
        this.service = service;
        this.interviewQuestionService = interviewQuestionService;
        this.shortlistService = shortlistService;
    }

    /**
     * Retrieves the candidates closest to the question by embedding similarity and lets the chat model
     * answer from their profiles.
     */
    @PostMapping("/api/v1/assistant/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return service.chat(request.question(), request.topK() == null ? CandidatePoolChatService.DEFAULT_TOP_K
                : request.topK());
    }

    /**
     * Generates interview questions tailored to the candidate and the job, using the language model.
     */
    @PostMapping("/api/v1/assistant/interview-questions")
    public InterviewQuestionsResponse interviewQuestions(@Valid @RequestBody InterviewQuestionsRequest request) {
        return interviewQuestionService.generate(request.jobId(), request.candidateId(),
                request.count() == null ? InterviewQuestionService.DEFAULT_COUNT : request.count());
    }

    /**
     * Lets a tool-calling agent rank candidates for the job, read their profiles and pick a shortlist.
     */
    @PostMapping("/api/v1/assistant/shortlist")
    public ShortlistResponse shortlist(@Valid @RequestBody ShortlistRequest request) {
        return shortlistService.shortlist(request.jobId(),
                request.size() == null ? ShortlistService.DEFAULT_SIZE : request.size());
    }
}

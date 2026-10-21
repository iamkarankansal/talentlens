package com.talentlens.assistant;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Recruiter assistant",
        description = "Questions answered from the candidate pool and tailored interview questions")
public class AssistantController {

    private final CandidatePoolChatService service;
    private final InterviewQuestionService interviewQuestionService;

    public AssistantController(CandidatePoolChatService service, InterviewQuestionService interviewQuestionService) {
        this.service = service;
        this.interviewQuestionService = interviewQuestionService;
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
}

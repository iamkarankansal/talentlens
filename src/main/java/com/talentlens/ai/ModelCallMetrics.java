package com.talentlens.ai;

import java.util.function.Supplier;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Times calls to the model provider. Every call is recorded under {@link #TIMER}, tagged with the operation and
 * whether it succeeded, so latency and failure rates show up per feature in {@code /actuator/metrics} and
 * {@code /actuator/prometheus}.
 */
@Component
public class ModelCallMetrics {

    public static final String TIMER = "talentlens.ai.model.calls";

    public static final String SKILL_EXTRACTION = "skill-extraction";
    public static final String MATCH_EXPLANATION = "match-explanation";
    public static final String RESUME_PARSING = "resume-parsing";
    public static final String INTERVIEW_QUESTIONS = "interview-questions";
    public static final String CANDIDATE_CHAT = "candidate-chat";
    public static final String EMAIL_DRAFT = "email-draft";
    public static final String SHORTLIST = "shortlist";
    public static final String EMBEDDING = "embedding";

    private final MeterRegistry registry;

    public ModelCallMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Runs the call and records how long it took. Exceptions are recorded with {@code outcome=failure} and
     * rethrown unchanged.
     */
    public <T> T record(String operation, Supplier<T> call) {
        Timer.Sample sample = Timer.start(registry);
        String outcome = "failure";
        try {
            T result = call.get();
            outcome = "success";
            return result;
        } finally {
            sample.stop(Timer.builder(TIMER)
                    .description("Calls to the AI model provider")
                    .tag("operation", operation)
                    .tag("outcome", outcome)
                    .register(registry));
        }
    }
}

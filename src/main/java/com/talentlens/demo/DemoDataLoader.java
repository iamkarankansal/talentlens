package com.talentlens.demo;

import java.time.Instant;
import java.util.List;

import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.job.Job;
import com.talentlens.job.JobRepository;
import com.talentlens.job.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Loads a few sample jobs and candidates at startup so a fresh installation has something to explore.
 * Only runs with {@code SEED_DEMO_DATA=true}, and only into a database without any jobs or candidates,
 * so it never mixes sample records with real ones. No model is called: candidates come with their
 * skills already filled in.
 */
@Component
@ConditionalOnProperty(prefix = "talentlens.demo", name = "seed", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final JobRepository jobRepository;
    private final CandidateRepository candidateRepository;

    public DemoDataLoader(JobRepository jobRepository, CandidateRepository candidateRepository) {
        this.jobRepository = jobRepository;
        this.candidateRepository = candidateRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /** Inserts the demo records and returns true, or returns false when the database already holds data. */
    boolean seed() {
        if (jobRepository.count() > 0 || candidateRepository.count() > 0) {
            log.info("Demo data not loaded: the database already contains jobs or candidates");
            return false;
        }
        List<Job> jobs = demoJobs();
        List<Candidate> candidates = demoCandidates();
        jobRepository.saveAll(jobs);
        candidateRepository.saveAll(candidates);
        log.info("Loaded demo data: {} jobs and {} candidates", jobs.size(), candidates.size());
        return true;
    }

    static List<Job> demoJobs() {
        return List.of(
                job("Senior Java Backend Engineer", "Bengaluru", 5, JobStatus.OPEN,
                        "Build and run the Spring Boot services behind our hiring platform. You will design REST APIs, "
                                + "model data in PostgreSQL and own reliability in production. Required: Java, Spring Boot, "
                                + "PostgreSQL, Docker. Nice to have: Kubernetes, Kafka."),
                job("Machine Learning Engineer", "Remote", 3, JobStatus.OPEN,
                        "Ship models that rank and explain candidate matches. Required: Python, PyTorch, embeddings "
                                + "and vector search, SQL. Nice to have: LLM prompt design and evaluation."),
                job("Frontend Engineer", "Pune", 2, JobStatus.DRAFT,
                        "Create the recruiter dashboard. Required: TypeScript, React, CSS, REST API integration. "
                                + "Nice to have: accessibility and design systems experience."));
    }

    static List<Candidate> demoCandidates() {
        return List.of(
                candidate("Aarav Sharma", "aarav.sharma@example.com", "Backend engineer focused on Java services",
                        6.0, List.of("Java", "Spring Boot", "PostgreSQL", "Docker", "Kafka"),
                        "Six years building Java and Spring Boot microservices on PostgreSQL, shipped with Docker "
                                + "and Kafka. Led the migration of a payments backend to containers."),
                candidate("Meera Iyer", "meera.iyer@example.com", "ML engineer working on search and ranking",
                        4.0, List.of("Python", "PyTorch", "SQL", "Vector Search"),
                        "Four years training ranking models in Python and PyTorch, building embedding pipelines and "
                                + "vector search on top of SQL warehouses."),
                candidate("Rohan Gupta", "rohan.gupta@example.com", "Frontend developer",
                        2.5, List.of("TypeScript", "React", "CSS"),
                        "Two and a half years building React and TypeScript dashboards with a focus on clean CSS "
                                + "and accessible components."),
                candidate("Sara Khan", "sara.khan@example.com", "Full-stack engineer",
                        3.0, List.of("Java", "React", "TypeScript", "Docker"),
                        "Three years across a Java backend and a React frontend, deploying both with Docker."));
    }

    private static Job job(String title, String location, int minExperienceYears, JobStatus status,
                           String description) {
        Job job = new Job();
        job.setTitle(title);
        job.setLocation(location);
        job.setMinExperienceYears(minExperienceYears);
        job.setStatus(status);
        job.setDescription(description);
        return job;
    }

    private static Candidate candidate(String fullName, String email, String headline, double experienceYears,
                                       List<String> skills, String resumeText) {
        Candidate candidate = new Candidate();
        candidate.setFullName(fullName);
        candidate.setEmail(email);
        candidate.setResumeText(resumeText);
        candidate.setProfile(headline, experienceYears, skills, Instant.now());
        return candidate;
    }
}

package com.talentlens.matching;

import java.util.ArrayList;
import java.util.List;

import com.talentlens.ai.SkillExtractor;
import com.talentlens.config.CacheConfig;
import com.talentlens.job.Job;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * The skills a job requires, extracted by the model once per job and then served from the cache, so
 * repeated skill-gap reports for the same job do not ask the model again. Updating or deleting the job
 * clears its entry. A model failure is not cached.
 */
@Component
public class RequiredSkillsCache {

    private final SkillExtractor skillExtractor;

    public RequiredSkillsCache(SkillExtractor skillExtractor) {
        this.skillExtractor = skillExtractor;
    }

    @Cacheable(cacheNames = CacheConfig.REQUIRED_SKILLS, key = "#job.id")
    public List<String> requiredSkills(Job job) {
        List<String> skills = skillExtractor.requiredSkills(EmbeddingService.jobText(job));
        // A plain ArrayList is serializable for Redis and keeps whatever the model returned for normalising later.
        return skills == null ? new ArrayList<>() : new ArrayList<>(skills);
    }
}

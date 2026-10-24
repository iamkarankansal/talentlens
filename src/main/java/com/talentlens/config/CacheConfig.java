package com.talentlens.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Caches job lookups and match results. The cache backend comes from {@code spring.cache.type}: Redis when
 * {@code CACHE_TYPE=redis}, an in-memory map by default, or {@code none} to switch caching off.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Single jobs by id. */
    public static final String JOBS = "jobs";

    /** Ranked candidates per job and limit. */
    public static final String CANDIDATE_RANKINGS = "candidateRankings";

    /** Model-written match explanations per job and candidate. */
    public static final String MATCH_EXPLANATIONS = "matchExplanations";
}

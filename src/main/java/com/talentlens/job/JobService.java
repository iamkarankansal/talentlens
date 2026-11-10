package com.talentlens.job;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.talentlens.common.ConflictException;
import com.talentlens.common.LikePattern;
import com.talentlens.common.PageResponse;
import com.talentlens.common.ResourceNotFoundException;
import com.talentlens.config.CacheConfig;
import jakarta.persistence.criteria.Predicate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class JobService {

    private final JobRepository repository;

    public JobService(JobRepository repository) {
        this.repository = repository;
    }

    public JobResponse create(JobRequest request) {
        Job job = new Job();
        apply(job, request);
        if (request.status() != null) {
            job.setStatus(request.status());
        }
        return JobResponse.from(repository.save(job));
    }

    @Transactional(readOnly = true)
    public PageResponse<JobResponse> findAll(JobStatus status, String location, Pageable pageable) {
        return PageResponse.from(repository.findAll(matching(status, location), pageable), JobResponse::from);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.JOBS, key = "#id")
    public JobResponse findById(Long id) {
        return JobResponse.from(getOrThrow(id));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.JOBS, key = "#id"),
            @CacheEvict(cacheNames = {CacheConfig.CANDIDATE_RANKINGS, CacheConfig.MATCH_EXPLANATIONS},
                    allEntries = true)
    })
    public JobResponse update(Long id, JobRequest request) {
        Job job = getOrThrow(id);
        apply(job, request);
        if (request.status() != null && request.status() != job.getStatus()) {
            moveTo(job, request.status());
        }
        return JobResponse.from(job);
    }

    @CacheEvict(cacheNames = CacheConfig.JOBS, key = "#id")
    public JobResponse changeStatus(Long id, JobStatus next) {
        Job job = getOrThrow(id);
        if (job.getStatus() == next) {
            return JobResponse.from(job);
        }
        moveTo(job, next);
        return JobResponse.from(job);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.JOBS, key = "#id"),
            @CacheEvict(cacheNames = {CacheConfig.CANDIDATE_RANKINGS, CacheConfig.MATCH_EXPLANATIONS},
                    allEntries = true)
    })
    public void delete(Long id) {
        getOrThrow(id).setDeletedAt(Instant.now());
    }

    /** Brings back a soft-deleted job together with the applications it kept. */
    @CacheEvict(cacheNames = CacheConfig.JOBS, key = "#id")
    public JobResponse restore(Long id) {
        Job job = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job", id));
        if (!job.isDeleted()) {
            throw new ConflictException("Job " + id + " is not deleted");
        }
        job.setDeletedAt(null);
        return JobResponse.from(job);
    }

    /** Loads a job that has not been deleted; deleted jobs are reported as not found. */
    public Job getOrThrow(Long id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job", id));
    }

    private Specification<Job> matching(JobStatus status, String location) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (location != null && !location.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("location")), LikePattern.contains(location),
                        LikePattern.ESCAPE));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void apply(Job job, JobRequest request) {
        job.setTitle(request.title().trim());
        job.setDescription(request.description().trim());
        job.setLocation(request.location());
        job.setMinExperienceYears(request.minExperienceYears());
        job.setClosesAt(request.closesAt());
    }

    private void moveTo(Job job, JobStatus next) {
        JobStatus current = job.getStatus();
        if (!current.canMoveTo(next)) {
            throw new ConflictException("Cannot move job " + job.getId() + " from " + current + " to " + next);
        }
        job.setStatus(next);
    }
}

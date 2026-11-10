package com.talentlens.job;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {

    /** Finds a job that has not been soft-deleted. */
    Optional<Job> findByIdAndDeletedAtIsNull(Long id);
}

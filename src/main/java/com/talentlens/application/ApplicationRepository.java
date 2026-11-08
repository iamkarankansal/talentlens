package com.talentlens.application;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByCandidateIdAndJobId(Long candidateId, Long jobId);

    @EntityGraph(attributePaths = {"candidate", "job"})
    Page<Application> findByJobId(Long jobId, Pageable pageable);

    @EntityGraph(attributePaths = {"candidate", "job"})
    Page<Application> findByJobIdAndStatus(Long jobId, ApplicationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"candidate", "job"})
    Page<Application> findByCandidateId(Long candidateId, Pageable pageable);

    /** Each row is the status and the number of the job's applications in it. */
    @Query("select a.status, count(a) from Application a where a.job.id = :jobId group by a.status")
    List<Object[]> countByStatusForJob(@Param("jobId") Long jobId);
}

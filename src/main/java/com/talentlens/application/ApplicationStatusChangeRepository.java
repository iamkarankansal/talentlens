package com.talentlens.application;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationStatusChangeRepository extends JpaRepository<ApplicationStatusChange, Long> {

    List<ApplicationStatusChange> findByApplicationIdOrderByIdAsc(Long applicationId);
}

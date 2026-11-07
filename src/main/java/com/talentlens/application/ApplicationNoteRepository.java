package com.talentlens.application;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationNoteRepository extends JpaRepository<ApplicationNote, Long> {

    List<ApplicationNote> findByApplicationIdOrderByIdAsc(Long applicationId);
}

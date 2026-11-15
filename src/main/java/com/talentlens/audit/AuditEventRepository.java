package com.talentlens.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Page<AuditEvent> findByEntityType(String entityType, Pageable pageable);

    Page<AuditEvent> findByEntityTypeAndEntityId(String entityType, Long entityId, Pageable pageable);
}

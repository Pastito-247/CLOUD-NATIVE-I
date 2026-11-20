package com.ecommerce.audit.repository;

import com.ecommerce.audit.model.AuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {

    @Query("SELECT a FROM AuditRecord a WHERE " +
            "(:eventType IS NULL OR :eventType = '' OR a.eventType = :eventType) AND " +
            "(:actor IS NULL OR :actor = '' OR LOWER(a.actor) = LOWER(:actor)) AND " +
            "(:from IS NULL OR :from = '' OR a.occurredAt >= :from) AND " +
            "(:to IS NULL OR :to = '' OR a.occurredAt <= :to) " +
            "ORDER BY a.occurredAt DESC")
    List<AuditRecord> search(
            @Param("eventType") String eventType,
            @Param("actor") String actor,
            @Param("from") String from,
            @Param("to") String to);
}

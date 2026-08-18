package com.flowforge.service;

import com.flowforge.domain.entity.AuditLog;
import com.flowforge.domain.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Persists audit records.
 *
 * <p>{@link #record} runs in a {@code REQUIRES_NEW} transaction. That matters because the
 * audit is written from an {@code @TransactionalEventListener(AFTER_COMMIT)}: by then the
 * business transaction has already committed and there is no active transaction, so the
 * audit write needs its own. It also means an audit failure can't roll back the business
 * change that already succeeded.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actor, String action, String entityType, Long entityId,
                       Map<String, Object> metadata) {
        AuditLog log = new AuditLog(actor, action, entityType, entityId);
        if (metadata != null && !metadata.isEmpty()) {
            log.setMetadata(writeJson(metadata));
        }
        auditLogRepository.save(log);
    }

    private String writeJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JacksonException e) {
            return null; // never fail an audit write over metadata serialization
        }
    }
}

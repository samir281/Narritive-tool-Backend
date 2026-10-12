package com.narrativenews.service;

import com.narrativenews.domain.AuditLog;
import com.narrativenews.repo.AuditLogRepository;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditLogRepository repo;
    public AuditService(AuditLogRepository repo) { this.repo = repo; }

    public void log(UUID adminId, String action, String entityType, UUID entityId) {
        AuditLog l = new AuditLog();
        l.setAdminId(adminId); l.setAction(action); l.setEntityType(entityType); l.setEntityId(entityId);
        repo.save(l);
    }
}

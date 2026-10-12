package com.narrativenews.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "audit_logs") @Getter @Setter
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private UUID adminId;
    @Column(nullable = false, length = 40) private String action;
    @Column(nullable = false, length = 20) private String entityType;
    @Column(nullable = false) private UUID entityId;
    @Column(nullable = false) private Instant createdAt = Instant.now();
}

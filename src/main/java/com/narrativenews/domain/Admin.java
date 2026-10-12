package com.narrativenews.domain;

import com.narrativenews.domain.Enums.AccountStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "admins") @Getter @Setter
public class Admin {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, unique = true, length = 255) private String email;
    @Column(nullable = false) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private AccountStatus status = AccountStatus.ACTIVE;
    private UUID createdBy;
    @Column(nullable = false) private Instant createdAt = Instant.now();
}

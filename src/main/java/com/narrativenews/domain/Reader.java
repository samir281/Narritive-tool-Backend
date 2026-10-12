package com.narrativenews.domain;

import com.narrativenews.domain.Enums.AccountStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "readers") @Getter @Setter
public class Reader {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 60) private String displayName;
    @Column(nullable = false, unique = true, length = 255) private String email;
    @Column(nullable = false) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private AccountStatus status = AccountStatus.ACTIVE;
    @Column(nullable = false) private Instant createdAt = Instant.now();
}

package com.narrativenews.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

/** Binary file stored in the database (replaces S3). Page images and post images live here. */
@Entity @Table(name = "media") @Getter @Setter
public class Media {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 60) private String contentType;
    @JdbcTypeCode(SqlTypes.VARBINARY) @Column(nullable = false, length = 16_777_216) private byte[] data;
    @Column(nullable = false) private Instant createdAt = Instant.now();

    public Media() {}
    public Media(String contentType, byte[] data) { this.contentType = contentType; this.data = data; }
}

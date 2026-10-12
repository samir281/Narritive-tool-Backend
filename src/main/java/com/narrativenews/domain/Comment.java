package com.narrativenews.domain;

import com.narrativenews.domain.Enums.CommentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "comments", indexes = @Index(name = "idx_comments_post", columnList = "post_id, status, created_at"))
@Getter @Setter
public class Comment {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "post_id", nullable = false) private UUID postId;
    @Column(nullable = false) private UUID readerId;
    @Column(nullable = false, length = 1000) private String body;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 10) private CommentStatus status = CommentStatus.VISIBLE;
    private UUID moderatedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private Instant updatedAt = Instant.now();

    @PreUpdate void touch() { updatedAt = Instant.now(); }
}

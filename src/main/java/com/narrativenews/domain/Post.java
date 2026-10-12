package com.narrativenews.domain;

import com.narrativenews.domain.Enums.PostStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "posts", indexes = @Index(name = "idx_posts_edition", columnList = "edition_id, status, sort_order"))
@Getter @Setter
public class Post {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "edition_id", nullable = false) private UUID editionId;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String body;   // plain text
    private UUID imageMediaId;
    @Column(name = "sort_order", nullable = false) private int position;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 10) private PostStatus status = PostStatus.DRAFT;
    @Column(nullable = false) private int likeCount;
    @Column(nullable = false) private int commentCount;
    @Column(nullable = false) private UUID createdBy;
    private Instant publishedAt;
    @Version private int version;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private Instant updatedAt = Instant.now();

    @PreUpdate void touch() { updatedAt = Instant.now(); }

    public void publish() { status = PostStatus.PUBLISHED; if (publishedAt == null) publishedAt = Instant.now(); }
    public void unpublish() { status = PostStatus.DRAFT; }
}

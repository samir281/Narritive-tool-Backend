package com.narrativenews.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "post_likes", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "reader_id"}),
       indexes = @Index(name = "idx_likes_reader", columnList = "reader_id, post_id"))
@Getter @Setter
public class PostLike {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "post_id", nullable = false) private UUID postId;
    @Column(name = "reader_id", nullable = false) private UUID readerId;
    @Column(nullable = false) private Instant createdAt = Instant.now();
}

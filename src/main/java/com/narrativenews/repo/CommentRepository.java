package com.narrativenews.repo;

import com.narrativenews.domain.Comment;
import com.narrativenews.domain.Enums.CommentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    // Newest first. Cursor = createdAt of the last item of the previous page.
    List<Comment> findByPostIdAndStatusOrderByCreatedAtDesc(UUID postId, CommentStatus status, Pageable pageable);
    List<Comment> findByPostIdAndStatusAndCreatedAtLessThanOrderByCreatedAtDesc(
            UUID postId, CommentStatus status, Instant before, Pageable pageable);
    List<Comment> findByPostIdAndStatusInOrderByCreatedAtDesc(UUID postId, Collection<CommentStatus> statuses, Pageable pageable);
    void deleteByPostIdIn(Collection<UUID> postIds);
}

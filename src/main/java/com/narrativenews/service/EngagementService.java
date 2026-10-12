package com.narrativenews.service;

import com.narrativenews.domain.Comment;
import com.narrativenews.domain.Edition;
import com.narrativenews.domain.Enums.CommentStatus;
import com.narrativenews.domain.Enums.EditionStatus;
import com.narrativenews.domain.Enums.PostStatus;
import com.narrativenews.domain.Post;
import com.narrativenews.domain.PostLike;
import com.narrativenews.domain.Reader;
import com.narrativenews.repo.*;
import com.narrativenews.web.DomainException;
import com.narrativenews.web.Dtos.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Likes, comments and comment moderation. Counters change in the same transaction as the row they count. */
@Service
public class EngagementService {
    private static final int PAGE_SIZE = 20;
    private static final long MINUTE = 60_000;

    private final PostRepository posts;
    private final EditionRepository editions;
    private final PostLikeRepository likes;
    private final CommentRepository comments;
    private final ReaderRepository readers;
    private final TransactionTemplate tx;
    private final RateLimiter limiter;
    private final AuditService audit;

    public EngagementService(PostRepository posts, EditionRepository editions, PostLikeRepository likes,
                             CommentRepository comments, ReaderRepository readers, TransactionTemplate tx,
                             RateLimiter limiter, AuditService audit) {
        this.posts = posts; this.editions = editions; this.likes = likes; this.comments = comments;
        this.readers = readers; this.tx = tx; this.limiter = limiter; this.audit = audit;
    }

    // ---------- likes ----------

    public LikeResult like(UUID readerId, UUID postId) {
        limiter.check("like:" + readerId, 60, MINUTE);
        requireVisiblePost(postId);
        try {
            tx.executeWithoutResult(s -> {
                if (!likes.existsByPostIdAndReaderId(postId, readerId)) {
                    PostLike l = new PostLike();
                    l.setPostId(postId);
                    l.setReaderId(readerId);
                    likes.saveAndFlush(l);
                    posts.addLikes(postId, 1);
                }
            });
        } catch (DataIntegrityViolationException alreadyLiked) {
            // double tap / retry raced us: the unique key stopped the duplicate, nothing to do
        }
        return new LikeResult(true, posts.likeCountOf(postId));
    }

    public LikeResult unlike(UUID readerId, UUID postId) {
        limiter.check("like:" + readerId, 60, MINUTE);
        requireVisiblePost(postId);
        tx.executeWithoutResult(s -> {
            if (likes.deleteLike(postId, readerId) > 0) posts.addLikes(postId, -1);
        });
        return new LikeResult(false, posts.likeCountOf(postId));
    }

    public List<UUID> myLikes(UUID readerId, UUID editionId) {
        List<UUID> ids = posts.findByEditionIdAndStatusOrderByPositionAsc(editionId, PostStatus.PUBLISHED)
                .stream().map(Post::getId).toList();
        return ids.isEmpty() ? List.of() : likes.likedPostIds(readerId, ids);
    }

    // ---------- comments (reader side) ----------

    public CommentPage listComments(UUID postId, String cursor) {
        requireVisiblePost(postId);
        PageRequest page = PageRequest.of(0, PAGE_SIZE);
        List<Comment> rows = (cursor == null || cursor.isBlank())
                ? comments.findByPostIdAndStatusOrderByCreatedAtDesc(postId, CommentStatus.VISIBLE, page)
                : comments.findByPostIdAndStatusAndCreatedAtLessThanOrderByCreatedAtDesc(postId, CommentStatus.VISIBLE, parseCursor(cursor), page);
        String next = rows.size() == PAGE_SIZE ? rows.get(rows.size() - 1).getCreatedAt().toString() : null;
        return new CommentPage(toViews(rows), next);
    }

    public CommentView addComment(UUID readerId, UUID postId, String body) {
        limiter.check("comment:" + readerId, 5, MINUTE);
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) throw DomainException.bad("VALIDATION_FAILED", "Comment cannot be empty");
        if (text.length() > 1000) throw DomainException.bad("COMMENT_TOO_LONG", "Comments are limited to 1000 characters");
        requireVisiblePost(postId);
        Comment saved = tx.execute(s -> {
            Comment c = new Comment();
            c.setPostId(postId);
            c.setReaderId(readerId);
            c.setBody(text);
            c = comments.save(c);
            posts.addComments(postId, 1);
            return c;
        });
        return toViews(List.of(Objects.requireNonNull(saved))).get(0);
    }

    public void deleteOwn(UUID readerId, UUID commentId) {
        tx.executeWithoutResult(s -> {
            Comment c = comments.findById(commentId)
                    .orElseThrow(() -> DomainException.notFound("COMMENT_NOT_FOUND", "Comment not found"));
            if (!c.getReaderId().equals(readerId)) throw DomainException.forbidden("You can only delete your own comments");
            if (c.getStatus() == CommentStatus.DELETED) return;
            boolean wasVisible = c.getStatus() == CommentStatus.VISIBLE;
            c.setStatus(CommentStatus.DELETED);
            comments.save(c);
            if (wasVisible) posts.addComments(c.getPostId(), -1);
        });
    }

    // ---------- moderation (admin side) ----------

    public List<CommentView> listForModeration(UUID postId) {
        List<Comment> rows = comments.findByPostIdAndStatusInOrderByCreatedAtDesc(
                postId, List.of(CommentStatus.VISIBLE, CommentStatus.HIDDEN), PageRequest.of(0, 200));
        return toViews(rows);
    }

    public void hide(UUID adminId, UUID commentId) {
        tx.executeWithoutResult(s -> {
            Comment c = comments.findById(commentId)
                    .orElseThrow(() -> DomainException.notFound("COMMENT_NOT_FOUND", "Comment not found"));
            if (c.getStatus() != CommentStatus.VISIBLE) return;
            c.setStatus(CommentStatus.HIDDEN);
            c.setModeratedBy(adminId);
            comments.save(c);
            posts.addComments(c.getPostId(), -1);
        });
        audit.log(adminId, "COMMENT_HIDDEN", "COMMENT", commentId);
    }

    public void restore(UUID adminId, UUID commentId) {
        tx.executeWithoutResult(s -> {
            Comment c = comments.findById(commentId)
                    .orElseThrow(() -> DomainException.notFound("COMMENT_NOT_FOUND", "Comment not found"));
            if (c.getStatus() != CommentStatus.HIDDEN) return;
            c.setStatus(CommentStatus.VISIBLE);
            c.setModeratedBy(adminId);
            comments.save(c);
            posts.addComments(c.getPostId(), 1);
        });
        audit.log(adminId, "COMMENT_RESTORED", "COMMENT", commentId);
    }

    // ---------- helpers ----------

    /** A post is visible to readers only if the post AND its edition are published. Drafts return 404, never 403. */
    private Post requireVisiblePost(UUID postId) {
        Post p = posts.findById(postId).filter(x -> x.getStatus() == PostStatus.PUBLISHED)
                .orElseThrow(() -> DomainException.notFound("POST_NOT_FOUND", "Post not found"));
        Edition e = editions.findById(p.getEditionId()).filter(x -> x.getStatus() == EditionStatus.PUBLISHED)
                .orElseThrow(() -> DomainException.notFound("POST_NOT_FOUND", "Post not found"));
        return p;
    }

    private List<CommentView> toViews(List<Comment> rows) {
        Set<UUID> readerIds = rows.stream().map(Comment::getReaderId).collect(Collectors.toSet());
        Map<UUID, String> names = readers.findAllById(readerIds).stream()
                .collect(Collectors.toMap(Reader::getId, Reader::getDisplayName));
        return rows.stream()
                .map(c -> new CommentView(c.getId(), c.getReaderId(), names.getOrDefault(c.getReaderId(), "Reader"),
                        c.getBody(), c.getCreatedAt(), c.getStatus().name()))
                .toList();
    }

    private static Instant parseCursor(String cursor) {
        try { return Instant.parse(cursor); }
        catch (Exception e) { throw DomainException.bad("VALIDATION_FAILED", "Invalid cursor"); }
    }
}

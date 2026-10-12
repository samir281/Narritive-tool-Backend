package com.narrativenews.service;

import com.narrativenews.domain.Enums.PostStatus;
import com.narrativenews.domain.Media;
import com.narrativenews.domain.Post;
import com.narrativenews.repo.*;
import com.narrativenews.web.DomainException;
import com.narrativenews.web.Dtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

@Service
public class PostService {
    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;

    private final PostRepository posts;
    private final EditionRepository editions;
    private final PostLikeRepository likes;
    private final CommentRepository comments;
    private final MediaRepository media;
    private final AuditService audit;

    public PostService(PostRepository posts, EditionRepository editions, PostLikeRepository likes,
                       CommentRepository comments, MediaRepository media, AuditService audit) {
        this.posts = posts; this.editions = editions; this.likes = likes; this.comments = comments;
        this.media = media; this.audit = audit;
    }

    public static AdminPostView toAdminView(Post p) {
        return new AdminPostView(p.getId(), p.getTitle(), p.getBody(), EditionService.mediaUrl(p.getImageMediaId()),
                p.getPosition(), p.getStatus().name(), p.getLikeCount(), p.getCommentCount());
    }

    @Transactional
    public AdminPostView create(UUID adminId, UUID editionId, String title, String body) {
        if (!editions.existsById(editionId)) throw DomainException.notFound("EDITION_NOT_FOUND", "Edition not found");
        Post p = new Post();
        p.setEditionId(editionId);
        p.setTitle(title.trim());
        p.setBody(body.trim());
        p.setPosition(posts.maxPosition(editionId) + 1);
        p.setCreatedBy(adminId);
        p = posts.save(p);
        audit.log(adminId, "POST_CREATED", "POST", p.getId());
        return toAdminView(p);
    }

    @Transactional
    public AdminPostView update(UUID adminId, UUID id, String title, String body) {
        Post p = load(id);
        if (title != null) p.setTitle(title.trim());
        if (body != null) p.setBody(body.trim());
        p = posts.save(p);
        audit.log(adminId, "POST_UPDATED", "POST", id);
        return toAdminView(p);
    }

    @Transactional
    public void reorder(UUID adminId, UUID editionId, List<UUID> orderedIds) {
        List<Post> existing = posts.findByEditionIdOrderByPositionAsc(editionId);
        Map<UUID, Post> byId = new HashMap<>();
        existing.forEach(p -> byId.put(p.getId(), p));
        if (orderedIds.size() != byId.size() || !byId.keySet().containsAll(orderedIds))
            throw DomainException.bad("VALIDATION_FAILED", "postIds must list every post of this edition exactly once");
        int pos = 1;
        for (UUID id : orderedIds) byId.get(id).setPosition(pos++);
        posts.saveAll(byId.values());
        audit.log(adminId, "POSTS_REORDERED", "EDITION", editionId);
    }

    @Transactional
    public AdminPostView setStatus(UUID adminId, UUID id, PostStatus status) {
        Post p = load(id);
        if (status == PostStatus.PUBLISHED) p.publish(); else p.unpublish();
        p = posts.save(p);
        audit.log(adminId, status == PostStatus.PUBLISHED ? "POST_PUBLISHED" : "POST_UNPUBLISHED", "POST", id);
        return toAdminView(p);
    }

    @Transactional
    public void delete(UUID adminId, UUID id) {
        Post p = load(id);
        likes.deleteByPostIdIn(List.of(id));
        comments.deleteByPostIdIn(List.of(id));
        if (p.getImageMediaId() != null) media.deleteById(p.getImageMediaId());
        posts.delete(p);
        audit.log(adminId, "POST_DELETED", "POST", id);
    }

    /** Accepts jpg/png/gif, shrinks to max 1200 px wide and stores as JPEG in the database. */
    @Transactional
    public AdminPostView setImage(UUID adminId, UUID id, MultipartFile file) {
        Post p = load(id);
        if (file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES)
            throw DomainException.bad("FILE_TOO_LARGE", "Image must be under 8 MB");
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
            if (img == null) throw DomainException.bad("INVALID_FILE_TYPE", "Use a JPG, PNG or GIF image");
            byte[] jpg = ImageCodec.jpeg(ImageCodec.scaleToWidth(img, 1200), 0.82f);
            Media m = media.save(new Media("image/jpeg", jpg));
            UUID old = p.getImageMediaId();
            p.setImageMediaId(m.getId());
            p = posts.save(p);
            if (old != null) media.deleteById(old);
            audit.log(adminId, "POST_IMAGE_SET", "POST", id);
            return toAdminView(p);
        } catch (IOException e) {
            throw DomainException.bad("UPLOAD_FAILED", "Could not read the image");
        }
    }

    @Transactional
    public AdminPostView removeImage(UUID adminId, UUID id) {
        Post p = load(id);
        UUID old = p.getImageMediaId();
        p.setImageMediaId(null);
        p = posts.save(p);
        if (old != null) media.deleteById(old);
        return toAdminView(p);
    }

    private Post load(UUID id) {
        return posts.findById(id).orElseThrow(() -> DomainException.notFound("POST_NOT_FOUND", "Post not found"));
    }
}

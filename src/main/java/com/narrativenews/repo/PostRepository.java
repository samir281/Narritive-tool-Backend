package com.narrativenews.repo;

import com.narrativenews.domain.Enums.PostStatus;
import com.narrativenews.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {
    List<Post> findByEditionIdOrderByPositionAsc(UUID editionId);
    List<Post> findByEditionIdAndStatusOrderByPositionAsc(UUID editionId, PostStatus status);
    int countByEditionId(UUID editionId);

    @Query("select coalesce(max(p.position), 0) from Post p where p.editionId = :editionId")
    int maxPosition(@Param("editionId") UUID editionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.likeCount = p.likeCount + :delta where p.id = :id")
    void addLikes(@Param("id") UUID id, @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.commentCount = p.commentCount + :delta where p.id = :id")
    void addComments(@Param("id") UUID id, @Param("delta") int delta);

    @Query("select p.likeCount from Post p where p.id = :id")
    int likeCountOf(@Param("id") UUID id);
}

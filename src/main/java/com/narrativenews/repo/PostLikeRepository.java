package com.narrativenews.repo;

import com.narrativenews.domain.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {
    boolean existsByPostIdAndReaderId(UUID postId, UUID readerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PostLike l where l.postId = :postId and l.readerId = :readerId")
    int deleteLike(@Param("postId") UUID postId, @Param("readerId") UUID readerId);

    @Query("select l.postId from PostLike l where l.readerId = :readerId and l.postId in :postIds")
    List<UUID> likedPostIds(@Param("readerId") UUID readerId, @Param("postIds") Collection<UUID> postIds);

    void deleteByPostIdIn(Collection<UUID> postIds);
}

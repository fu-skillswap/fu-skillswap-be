package com.fptu.exe.skillswap.modules.forum.repository;

import com.fptu.exe.skillswap.modules.forum.domain.ForumPostReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ForumPostReactionRepository extends JpaRepository<ForumPostReaction, UUID> {

    Optional<ForumPostReaction> findByPostIdAndUserId(UUID postId, UUID userId);

    @Query("""
            select reaction.post.id
            from ForumPostReaction reaction
            where reaction.user.id = :userId
              and reaction.post.id in :postIds
            """)
    List<UUID> findReactedPostIdsByUserIdAndPostIdIn(@Param("userId") UUID userId,
                                                     @Param("postIds") Collection<UUID> postIds);

    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from ForumPostReaction r where r.post.id = :postId")
    void deleteByPostId(@Param("postId") UUID postId);
}

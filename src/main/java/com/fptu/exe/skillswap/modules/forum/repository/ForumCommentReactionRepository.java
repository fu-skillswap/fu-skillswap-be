package com.fptu.exe.skillswap.modules.forum.repository;

import com.fptu.exe.skillswap.modules.forum.domain.ForumCommentReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ForumCommentReactionRepository extends JpaRepository<ForumCommentReaction, UUID> {

    Optional<ForumCommentReaction> findByCommentIdAndUserId(UUID commentId, UUID userId);

    @Query("select r.comment.id from ForumCommentReaction r where r.user.id = :userId and r.comment.id in :commentIds")
    List<UUID> findReactedCommentIdsByUserIdAndCommentIdIn(
            @Param("userId") UUID userId,
            @Param("commentIds") Collection<UUID> commentIds
    );
}

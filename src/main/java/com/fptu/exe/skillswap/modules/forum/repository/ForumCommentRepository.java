package com.fptu.exe.skillswap.modules.forum.repository;

import com.fptu.exe.skillswap.modules.forum.domain.ForumComment;
import com.fptu.exe.skillswap.modules.forum.domain.ForumCommentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Collection;
import java.util.List;

@Repository
public interface ForumCommentRepository extends JpaRepository<ForumComment, UUID>, ForumCommentRepositoryCustom {

    @EntityGraph(attributePaths = {"authorUser", "post", "post.authorUser", "post.helpTopic"})
    Optional<ForumComment> findById(UUID id);

    @EntityGraph(attributePaths = {"authorUser", "post", "post.authorUser", "post.helpTopic"})
    List<ForumComment> findByIdIn(Collection<UUID> ids);

    @EntityGraph(attributePaths = {"authorUser", "post", "post.authorUser", "post.helpTopic"})
    List<ForumComment> findByReplyToCommentIdAndStatus(UUID replyToCommentId, ForumCommentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ForumComment c where c.id = :id")
    Optional<ForumComment> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select count(c.id) > 0
            from ForumComment c
            where c.post.id = :postId
              and c.authorUser.id = :authorUserId
              and lower(c.content) = lower(:content)
              and c.createdAt >= :createdAfter
            """)
    boolean existsRecentDuplicateComment(
            @Param("postId") UUID postId,
            @Param("authorUserId") UUID authorUserId,
            @Param("content") String content,
            @Param("createdAfter") LocalDateTime createdAfter
    );
    @org.springframework.data.jpa.repository.Modifying
    @Query("update ForumComment c set c.deletedAt = current_timestamp where c.post.id = :postId")
    void softDeleteByPostId(@Param("postId") UUID postId);
}

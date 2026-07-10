package com.fptu.exe.skillswap.modules.forum.repository;

import com.fptu.exe.skillswap.modules.forum.domain.ForumPost;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPostStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
public interface ForumPostRepository extends JpaRepository<ForumPost, UUID>, JpaSpecificationExecutor<ForumPost>, ForumPostRepositoryCustom {

    @EntityGraph(attributePaths = {"authorUser", "helpTopic"})
    Optional<ForumPost> findByIdAndStatus(UUID id, ForumPostStatus status);

    @EntityGraph(attributePaths = {"authorUser", "helpTopic"})
    Optional<ForumPost> findById(UUID id);

    @EntityGraph(attributePaths = {"authorUser", "helpTopic"})
    List<ForumPost> findByIdIn(Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ForumPost p where p.id = :id")
    Optional<ForumPost> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select count(p.id) > 0
            from ForumPost p
            where p.authorUser.id = :authorUserId
              and lower(p.title) = lower(:title)
              and lower(p.content) = lower(:content)
              and p.createdAt >= :createdAfter
            """)
    boolean existsRecentDuplicatePost(
            @Param("authorUserId") UUID authorUserId,
            @Param("title") String title,
            @Param("content") String content,
            @Param("createdAfter") LocalDateTime createdAfter
    );
}

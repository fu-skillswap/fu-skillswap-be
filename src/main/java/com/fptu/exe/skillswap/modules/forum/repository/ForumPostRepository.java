package com.fptu.exe.skillswap.modules.forum.repository;

import com.fptu.exe.skillswap.modules.forum.domain.ForumPost;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.List;

@Repository
public interface ForumPostRepository extends JpaRepository<ForumPost, UUID> {

    @EntityGraph(attributePaths = {"authorUser", "helpTopic"})
    @Query("""
            select p
            from ForumPost p
            join p.authorUser u
            where p.status = :status
              and (:helpTopicId is null or p.helpTopic.id = :helpTopicId)
              and (:authorId is null or u.id = :authorId)
              and (
                    :keywordPattern is null
                    or lower(p.title) like :keywordPattern
                    or lower(p.content) like :keywordPattern
                    or lower(u.fullName) like :keywordPattern
              )
            """)
    Page<ForumPost> searchPublicPosts(@Param("status") ForumPostStatus status,
                                      @Param("helpTopicId") UUID helpTopicId,
                                      @Param("authorId") UUID authorId,
                                      @Param("keywordPattern") String keywordPattern,
                                      Pageable pageable);

    @EntityGraph(attributePaths = {"authorUser", "helpTopic"})
    Optional<ForumPost> findByIdAndStatus(UUID id, ForumPostStatus status);

    @EntityGraph(attributePaths = {"authorUser", "helpTopic"})
    @Query("""
            select p
            from ForumPost p
            join p.authorUser u
            where (:status is null or p.status = :status)
              and (:helpTopicId is null or p.helpTopic.id = :helpTopicId)
              and (:authorId is null or u.id = :authorId)
              and (
                    :keywordPattern is null
                    or lower(p.title) like :keywordPattern
                    or lower(p.content) like :keywordPattern
                    or lower(u.fullName) like :keywordPattern
              )
            """)
    Page<ForumPost> searchAdminPosts(@Param("status") ForumPostStatus status,
                                     @Param("helpTopicId") UUID helpTopicId,
                                     @Param("authorId") UUID authorId,
                                     @Param("keywordPattern") String keywordPattern,
                                     Pageable pageable);

    @EntityGraph(attributePaths = {"authorUser", "helpTopic"})
    Optional<ForumPost> findById(UUID id);

    @EntityGraph(attributePaths = {"authorUser", "helpTopic"})
    List<ForumPost> findByIdIn(Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ForumPost p where p.id = :id")
    Optional<ForumPost> findByIdForUpdate(@Param("id") UUID id);
}

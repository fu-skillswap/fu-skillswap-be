package com.fptu.exe.skillswap.modules.blog.repository;

import com.fptu.exe.skillswap.modules.blog.domain.BlogPost;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;

public interface BlogPostRepository extends JpaRepository<BlogPost, UUID>, BlogPostRepositoryCustom {

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    @EntityGraph(attributePaths = {"authorUser", "categories", "tags"})
    Optional<BlogPost> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BlogPost p where p.id = :postId")
    Optional<BlogPost> findByIdForEngagementUpdate(@Param("postId") UUID postId);

    @EntityGraph(attributePaths = {"authorUser", "categories", "tags"})
    Optional<BlogPost> findBySlug(String slug);

    @Query("""
            select p
            from BlogPost p
            join fetch p.authorUser
            left join fetch p.categories
            left join fetch p.tags
            where p.status = :status
              and p.featured = true
              and p.publishedAt is not null
              and (p.featuredUntil is null or p.featuredUntil > :now)
            order by p.featuredOrder asc nulls last, p.publishedAt desc, p.id desc
            """)
    List<BlogPost> findFeatured(
            @Param("status") BlogPostStatus status,
            @Param("now") LocalDateTime now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BlogPost p set p.viewCount = p.viewCount + 1 where p.id = :postId")
    int incrementViewCount(@Param("postId") UUID postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BlogPost p set p.likeCount = p.likeCount + 1 where p.id = :postId")
    int incrementLikeCount(@Param("postId") UUID postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BlogPost p set p.likeCount = case when p.likeCount > 0 then p.likeCount - 1 else 0 end where p.id = :postId")
    int decrementLikeCount(@Param("postId") UUID postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BlogPost p set p.bookmarkCount = p.bookmarkCount + 1 where p.id = :postId")
    int incrementBookmarkCount(@Param("postId") UUID postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BlogPost p set p.bookmarkCount = case when p.bookmarkCount > 0 then p.bookmarkCount - 1 else 0 end where p.id = :postId")
    int decrementBookmarkCount(@Param("postId") UUID postId);

    @Query("""
            select distinct p
            from BlogPost p
            join fetch p.authorUser
            left join fetch p.categories c
            left join fetch p.tags t
            where p.status = :status
              and p.id <> :excludedPostId
              and p.visibility in :allowedVisibilities
              and p.visibility = :sourceVisibility
              and p.publishedAt is not null
              and (
                    (:categoryIdsEmpty = false and c.id in :categoryIds)
                    or (:tagIdsEmpty = false and t.id in :tagIds)
                    or p.audienceType = :audienceType
              )
            order by p.publishedAt desc, p.likeCount desc, p.bookmarkCount desc, p.id desc
            """)
    List<BlogPost> findRelatedCandidates(
            @Param("status") BlogPostStatus status,
            @Param("excludedPostId") UUID excludedPostId,
            @Param("allowedVisibilities") Collection<BlogVisibility> allowedVisibilities,
            @Param("sourceVisibility") BlogVisibility sourceVisibility,
            @Param("categoryIds") Collection<UUID> categoryIds,
            @Param("categoryIdsEmpty") boolean categoryIdsEmpty,
            @Param("tagIds") Collection<UUID> tagIds,
            @Param("tagIdsEmpty") boolean tagIdsEmpty,
            @Param("audienceType") com.fptu.exe.skillswap.modules.blog.domain.BlogAudienceType audienceType,
            Pageable pageable
    );

    @Query("""
            select p
            from BlogPost p
            join fetch p.authorUser
            left join fetch p.categories
            left join fetch p.tags
            where p.status = :status
              and p.visibility in :allowedVisibilities
              and p.publishedAt is not null
            order by (p.likeCount * 5 + p.bookmarkCount * 8 + p.viewCount) desc,
                     p.publishedAt desc,
                     p.id desc
            """)
    List<BlogPost> findTrendingCandidates(
            @Param("status") BlogPostStatus status,
            @Param("allowedVisibilities") Collection<BlogVisibility> allowedVisibilities,
            Pageable pageable
    );
}

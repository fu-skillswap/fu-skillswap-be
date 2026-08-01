package com.fptu.exe.skillswap.modules.blog.repository;

import com.fptu.exe.skillswap.modules.blog.domain.BlogPost;
import com.fptu.exe.skillswap.modules.blog.domain.BlogAuthorType;
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

    interface MentorPublicAuthorityProjection {
        long getPublishedArticleCount();
        LocalDateTime getLatestPublishedAt();
    }

    @Query("""
            select count(p) as publishedArticleCount, max(p.publishedAt) as latestPublishedAt
            from BlogPost p
            where p.authorType = com.fptu.exe.skillswap.modules.blog.domain.BlogAuthorType.MENTOR
              and p.authorUser.id = :mentorUserId
              and p.status = com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus.PUBLISHED
              and p.visibility = com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility.PUBLIC
              and p.publishedAt is not null
            """)
    MentorPublicAuthorityProjection getMentorPublicAuthority(@Param("mentorUserId") UUID mentorUserId);

    @Query("""
            select p
            from BlogPost p
            where p.authorType = :authorType
              and p.authorUser.id = :mentorUserId
              and p.status = :status
              and p.visibility = :visibility
              and p.publishedAt is not null
            order by p.publishedAt desc, p.id desc
            """)
    List<BlogPost> findMentorPublicProfilePreviews(
            @Param("mentorUserId") UUID mentorUserId,
            @Param("authorType") BlogAuthorType authorType,
            @Param("status") BlogPostStatus status,
            @Param("visibility") BlogVisibility visibility,
            Pageable pageable
    );

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    @EntityGraph(attributePaths = {"authorUser", "categories", "tags"})
    Optional<BlogPost> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BlogPost p where p.id = :postId")
    Optional<BlogPost> findByIdForEngagementUpdate(@Param("postId") UUID postId);

    @EntityGraph(attributePaths = {"authorUser", "categories", "tags"})
    Optional<BlogPost> findBySlug(String slug);

    @EntityGraph(attributePaths = "authorUser")
    @Query("select p from BlogPost p where p.id in :postIds")
    List<BlogPost> findReaderPostsWithAuthorByIdIn(@Param("postIds") Collection<UUID> postIds);

    @Query("select distinct p from BlogPost p left join fetch p.categories where p.id in :postIds")
    List<BlogPost> loadCategoriesByPostIdIn(@Param("postIds") Collection<UUID> postIds);

    @Query("select distinct p from BlogPost p left join fetch p.tags where p.id in :postIds")
    List<BlogPost> loadTagsByPostIdIn(@Param("postIds") Collection<UUID> postIds);

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
            Pageable pageable
    );

    @Query("""
            select p.id
            from BlogPost p
            where p.status = :status
              and p.visibility in :allowedVisibilities
              and p.publishedAt is not null
            order by (p.likeCount * 5 + p.bookmarkCount * 8 + p.viewCount) desc,
                     p.publishedAt desc,
                     p.id desc
            """)
    List<UUID> findTrendingCandidateIds(
            @Param("status") BlogPostStatus status,
            @Param("allowedVisibilities") Collection<BlogVisibility> allowedVisibilities,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"authorUser", "categories", "tags", "entitledServices"})
    @Query("""
            select distinct p from BlogPost p join p.entitledServices s
            where p.status = com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus.PUBLISHED
              and p.visibility = com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility.BOOKED_MEMBERS
              and s.id = :serviceId
              and p.publishedAt is not null
            order by p.publishedAt desc, p.id desc
            """)
    List<BlogPost> findPremiumLibraryByServiceId(@Param("serviceId") UUID serviceId, Pageable pageable);

    // Native queries intentionally bypass the reader-facing soft-delete restriction for CMS recovery.
    @Query(value = "select * from blog_posts where deleted_at is not null and id = :postId", nativeQuery = true)
    Optional<BlogPost> findDeletedByIdForAdmin(@Param("postId") UUID postId);

    @Query(value = "select * from blog_posts where deleted_at is not null order by updated_at desc, id desc", nativeQuery = true)
    List<BlogPost> findDeletedForAdmin(Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update blog_posts set deleted_at = now(), updated_at = now(), version = version + 1 where id = :postId and deleted_at is null and version = :expectedVersion", nativeQuery = true)
    int softDeleteByIdAndVersion(@Param("postId") UUID postId, @Param("expectedVersion") int expectedVersion);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update blog_posts set deleted_at = null, status = 'ARCHIVED', updated_at = now(), version = version + 1 where id = :postId and deleted_at is not null and version = :expectedVersion", nativeQuery = true)
    int restoreByIdAndVersion(@Param("postId") UUID postId, @Param("expectedVersion") int expectedVersion);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BlogPost p
            set p.status = com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus.ARCHIVED,
                p.featured = false,
                p.featuredOrder = null,
                p.featuredUntil = null
            where p.authorType = com.fptu.exe.skillswap.modules.blog.domain.BlogAuthorType.MENTOR
              and p.authorUser.id = :authorUserId
              and p.status = com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus.PUBLISHED
            """)
    int archivePublishedMentorPostsByAuthor(@Param("authorUserId") UUID authorUserId);
}

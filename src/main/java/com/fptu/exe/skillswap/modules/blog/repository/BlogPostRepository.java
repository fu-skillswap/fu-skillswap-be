package com.fptu.exe.skillswap.modules.blog.repository;

import com.fptu.exe.skillswap.modules.blog.domain.BlogPost;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlogPostRepository extends JpaRepository<BlogPost, UUID>, BlogPostRepositoryCustom {

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    @EntityGraph(attributePaths = {"authorUser", "categories", "tags"})
    Optional<BlogPost> findById(UUID id);

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
}

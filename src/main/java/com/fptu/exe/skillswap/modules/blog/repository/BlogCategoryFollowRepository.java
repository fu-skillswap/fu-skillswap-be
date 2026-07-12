package com.fptu.exe.skillswap.modules.blog.repository;

import com.fptu.exe.skillswap.modules.blog.domain.BlogCategoryFollow;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface BlogCategoryFollowRepository extends JpaRepository<BlogCategoryFollow, UUID> {

    boolean existsByUserIdAndCategoryId(UUID userId, UUID categoryId);

    void deleteByUserIdAndCategoryId(UUID userId, UUID categoryId);

    @EntityGraph(attributePaths = "category")
    List<BlogCategoryFollow> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("select f.category.id from BlogCategoryFollow f where f.user.id = :userId")
    Set<UUID> findCategoryIdsByUserId(@Param("userId") UUID userId);

    @Query("select distinct f.user.id from BlogCategoryFollow f where f.category.id in :categoryIds")
    Set<UUID> findFollowerUserIdsByCategoryIds(@Param("categoryIds") Collection<UUID> categoryIds);
}

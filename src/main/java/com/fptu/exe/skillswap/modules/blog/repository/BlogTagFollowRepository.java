package com.fptu.exe.skillswap.modules.blog.repository;

import com.fptu.exe.skillswap.modules.blog.domain.BlogTagFollow;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface BlogTagFollowRepository extends JpaRepository<BlogTagFollow, UUID> {

    boolean existsByUserIdAndTagId(UUID userId, UUID tagId);

    void deleteByUserIdAndTagId(UUID userId, UUID tagId);

    @EntityGraph(attributePaths = "tag")
    List<BlogTagFollow> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("select f.tag.id from BlogTagFollow f where f.user.id = :userId")
    Set<UUID> findTagIdsByUserId(@Param("userId") UUID userId);

    @Query("select distinct f.user.id from BlogTagFollow f where f.tag.id in :tagIds")
    Set<UUID> findFollowerUserIdsByTagIds(@Param("tagIds") Collection<UUID> tagIds);
}

package com.fptu.exe.skillswap.modules.blog.repository;

import com.fptu.exe.skillswap.modules.blog.domain.BlogBookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface BlogBookmarkRepository extends JpaRepository<BlogBookmark, UUID>, BlogBookmarkRepositoryCustom {

    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    void deleteByPostIdAndUserId(UUID postId, UUID userId);

    @Query("select b.post.id from BlogBookmark b where b.user.id = :userId and b.post.id in :postIds")
    Set<UUID> findBookmarkedPostIds(@Param("userId") UUID userId, @Param("postIds") Collection<UUID> postIds);
}

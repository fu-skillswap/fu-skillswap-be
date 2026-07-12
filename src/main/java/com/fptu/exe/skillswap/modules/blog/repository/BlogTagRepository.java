package com.fptu.exe.skillswap.modules.blog.repository;

import com.fptu.exe.skillswap.modules.blog.domain.BlogTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlogTagRepository extends JpaRepository<BlogTag, UUID> {
    List<BlogTag> findByActiveTrueOrderByNameAsc();

    List<BlogTag> findByIdInAndActiveTrue(Collection<UUID> ids);

    Optional<BlogTag> findBySlug(String slug);

    boolean existsBySlug(String slug);
}

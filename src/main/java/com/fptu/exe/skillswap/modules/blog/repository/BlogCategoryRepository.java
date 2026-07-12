package com.fptu.exe.skillswap.modules.blog.repository;

import com.fptu.exe.skillswap.modules.blog.domain.BlogCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlogCategoryRepository extends JpaRepository<BlogCategory, UUID> {
    List<BlogCategory> findByActiveTrueOrderByDisplayOrderAscNameAsc();

    List<BlogCategory> findByIdInAndActiveTrue(Collection<UUID> ids);

    Optional<BlogCategory> findByCodeIgnoreCase(String code);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);
}
